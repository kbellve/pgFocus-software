import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import g4p_controls.*; 
import processing.serial.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class pgFocus extends PApplet {


// G4P can be downloaded from http://sourceforge.net/projects/g4p/



// Karl Bellve
// Biomedical Imaging Group

static int REGRESSIONPOINTS = 31;
static float MAX_DAU = 16384;
static float MIN_DAU = 5000;
static float MIDDLE_DAU = (MAX_DAU/2);
static float FIVE_HUNDRED_NM = 82; // DAUPERVOLT/MICRONPERVOLT/2 = 81.9
static float MAX_VOLT = 5;
static float MIN_VOLT = -5;
static int MICRONPERVOLT = 10;
static int DAUPERVOLT = 1638; // 14bit over 10V
//static int DAUPERVOLT = 1241; // 12bit over 3.3V
static int ADC_TRIGGER = 5;


String[] light,stats,cal; 
float fExposure = 0, fFocus = 0,fSlope = 0,fIntercept = 0,fResiduals = 0,fDAU = 0,fNewMark = 0, fOldMark = 0, fDiffMark = 0.0f;
float[][] fRegressionPoints = new float [REGRESSIONPOINTS][2];
float[] focusPoints = new float[30];
int diffADC = 0;
int focusPointsIndex = 0;
int focusPointsMax = 0;
int regressionPoints = 0;
int nMin, nMax;
int nKey = 0;
boolean bSwitch = false;
Serial pgFocus;         // The serial port object
int xPos = 110;          // horizontal position of the graph
PImage kiwi;
boolean bVerbose = false;
boolean bFocus = false;
Boolean clearGUI = true;    

GButton guiFocus,guiDown,guiUp,guiMark;

public void setup() {
  size(800,600);
  // BLUE_SCHEME, GREEN_SCHEME, RED_SCHEME, GREY_SCHEME
  // YELLOW_SCHEME, CYAN_SCHEME, PURPLE_SCHEME
  G4P.setGlobalColorScheme(GCScheme.BLUE_SCHEME);
  
  // big issue with processing. Arduino virtual ports are not recognized by Processing
  // softlink to /dev/ttyACM? to /dev/ttyS4
  println(Serial.list());
  
  if (Serial.list().length == 0) 
  {
    println("Couldn't find a serial port");
    println("Please make sure you have installed the Arduino Serial Driver");
    exit();
  }
  else {
    for (int x = 0; x < Serial.list().length; x++) {
      println("Found Serial Port: " +  Serial.list()[x]);
    }
    println("Using Serial Port: " +  Serial.list()[0]);
    
    pgFocus = new Serial(this,Serial.list()[0],57600); 

    // don't generate a serialEvent() unless you get a newline character:  
    
    pgFocus.bufferUntil('\n'); 
   
    kiwi = loadImage("kiwi.png");
    
    textSize(12);
    
    
    background(255);
    
    // buttons
    guiFocus = new GButton (this,5,180,90,20, "Focus Off"); 
    guiFocus.setTextAlign( GAlign.CENTER,GAlign.MIDDLE);
    guiFocus.fireAllEvents(false);
    
    guiUp = new GButton (this,5,210,90,20,"UP"); 
    guiUp.setTextAlign( GAlign.CENTER,GAlign.MIDDLE);
    guiUp.fireAllEvents(false);
    
    guiDown = new GButton (this,5,240,90,20,"DOWN"); 
    guiDown.setTextAlign( GAlign.CENTER, GAlign.MIDDLE);
    guiDown.fireAllEvents(false);
    
    guiMark = new GButton (this,5,270,90,20,"Mark"); 
    guiMark.setTextAlign( GAlign.CENTER, GAlign.MIDDLE);
    guiMark.fireAllEvents(false);
    
    hideGUI();
   
    // stop everything
    sendLetter('s');
   
    help();
    
    focusPointsMax = 0;
  }
  
}

public void hideGUI() {
  guiFocus.setVisible(false);
  guiFocus.setEnabled(false); 
  guiUp.setVisible(false);
  guiUp.setEnabled(false); 
  guiDown.setVisible(false);
  guiDown.setEnabled(false); 
  guiMark.setVisible(false);
  guiMark.setEnabled(false); 
  
}

public void showGUI() {
  guiFocus.setVisible(true);
  guiFocus.setEnabled(true); 
  guiUp.setVisible(true);
  guiUp.setEnabled(true); 
  guiDown.setVisible(true);
  guiDown.setEnabled(true); 
  guiMark.setVisible(true);
  guiMark.setEnabled(true); 
  
}

public void help() {
  background(255);
  fill(0);
  
  hideGUI();
  
  textAlign(CENTER,TOP);
  
  textSize(40);
  text("pgFocus",width/2,40);
  textSize(10);
  text("By Karl Bellve",width/2,100);
  textSize(14);
  
  textAlign(LEFT,TOP);
   
  text("Help:",width/2 -150,150);text("h",width/2 + 100,150);
  text("Calibrate pgFocus: ",width/2-150,175); text("c",width/2 + 100,175);
  text("Light intensity profile: ",width/2 -150,200);text("l",width/2 + 100,200);
  text("Show running output: ",width/2 -150,225);text("v",width/2 + 100,225);
  text("Save screen as tif file: ",width/2 -150,250);text("s",width/2 + 100,250);
 
  text("Activate pgFocus: ",width/2-150,300); text("f",width/2 + 100,300);
  text("Adjust Focus Up: ",width/2 -150,325);text("u",width/2 + 100,325);
  text("Adjust Focus Down: ",width/2 -150,350);text("d",width/2 + 100,350);
  
  text("Mark Position: ",width/2 -150,400);text("m",width/2 + 100,400);
 
  textAlign(CENTER,TOP);
  textSize(10);
  text("This software uses the first serial port, but Proccessing doesn't scan Arduino virtual serial ports.",width/2,450);
  text("A softlink must be created between the Arduino virtual serial port and a non existing serial port.",width/2,470);
  text("This is handled by the script pgFocus.bash and is started when the computer starts.",width/2,490);
  textAlign(LEFT,TOP);
  text("Biomedical Imaging Group",width * 3/4,540);
  text("University of Massachusetts",width * 3/4,555);
  text("http://big.umassmed.edu",width * 3/4,570);
  
  //scale(.1);
  image(kiwi,20,550,60,45.7f);
  
  
  textSize(12);

}

public void draw() {
  
  float xVal, yVal, lastxVal = 0,lastyVal = 0; 
      
  if (bFocus) {
    guiFocus.setLocalColorScheme(GCScheme.YELLOW_SCHEME);
    guiFocus.setText("Focus ON");
  }
  else {
    guiFocus.setLocalColorScheme(GCScheme.BLUE_SCHEME);
    guiFocus.setText("Focus OFF");  
  }  
    
  if (nKey == 1) {
    hideGUI();
    clearGUI = true;
    strokeWeight(2);
    background (255);
    
    if (light != null) {
      lastxVal = 0; lastyVal = 0;
      for (int x = 2; x < light.length; x++) {  
        // convert to an float and map to the screen height:
        yVal = PApplet.parseFloat(light[x]);
        yVal = map(yVal, 0, 1023, 0, height);
        xVal = map(x-2, 0, 127, 0, width);
        stroke(255,100,100);
        if (lastyVal == 0) lastyVal = yVal;
        line(lastxVal, height-lastyVal, xVal, height - yVal);
        lastxVal = xVal;
        lastyVal = yVal;
      }
    }
  }
  
  if (nKey == 2) {
    strokeWeight(2);
    
    if (stats != null) {
      //println (stats);
      fill(255);
      noStroke();
      if (clearGUI == true) {
        xPos = 100;
        rect(xPos,0,width,height); 
        clearGUI = false;
      }
      else rect(xPos,0,55,height); 
       
      rect(0,0,100,height); 
      stroke(0,0,0);
      line(100,0, 100, height);
      //guiFocus.draw();
      showGUI();
      
      // draw stats 6 and stats 7 first so that other lines can write on top
      if (stats.length > 7) {
        if (stats[6] != null) {
          stroke(0,0,255);
          fill(0,0,255);
  
          fFocus = PApplet.parseFloat(stats[6]);
          float ftempFocus = fFocus;
          focusPoints[focusPointsIndex++] = fFocus;
          if (focusPointsMax < 30) focusPointsMax++;
          if (focusPointsIndex == 30) focusPointsIndex = 0;
                 
          ftempFocus = map(fFocus,20,108,10,height-10);
          
          textAlign(RIGHT,BOTTOM);
          text(str(truncate(PApplet.parseFloat(stats[6]))),xPos + textWidth(str(truncate(PApplet.parseFloat(stats[6]))))+ 5,height - ftempFocus );
          textAlign(LEFT,BOTTOM);
          text("Focus: " ,5,60);
          text(str(truncate(PApplet.parseFloat(stats[6]))),50,60);
          
          
        //}
        
        //if (stats[7] != null) {
          float fSD = (standard_deviation(focusPoints,focusPointsMax) * fDAU/DAUPERVOLT ) * MICRONPERVOLT * 1000; // convert to nanometers from DAU
          float sd = sqrt(fSD);
          if (abs(diffADC) > ADC_TRIGGER || bFocus == false) stroke(160,160,215);
          else stroke(200,200,255);
          sd = map(sd,0,100,1,height/2);
          line(xPos,height-ftempFocus+sd,xPos,height-ftempFocus-sd);
          stroke(0,0,255);
          point(xPos,height - ftempFocus);
          fill(0,0,0);
          textAlign(LEFT,BOTTOM);
          text("SD: " ,5,100);
          text(str(truncate(fSD)),50,100);
        }
      }
      
      if (stats.length > 3) {
        if (stats[3] != null) {
          stroke(255,0,0);
          fill(255,0,0);
          nMin = PApplet.parseInt(stats[3]);
          nMin = PApplet.parseInt(map(nMin,0,1023,10,height-10));
          point(xPos,height - nMin);
          if (nMin < 500) textAlign(RIGHT,BOTTOM);
          else textAlign(RIGHT,TOP);
          text(stats[3],xPos + textWidth(stats[3]) + 5,height - nMin );
          textAlign(LEFT,BOTTOM);
          text("Min: ",5,40);
          text(stats[3],50,40);
        } 
      }
     
      if (stats.length > 4 ){
        if (stats[4] != null) {
          stroke(255,0,0);
          fill(255,0,0);      
          nMax = PApplet.parseInt(stats[4]);
          nMax = PApplet.parseInt(map(nMax,0,1023,10,height-10));
          point(xPos,height - nMax);
          if (nMax > 500) textAlign(RIGHT,TOP);
          else textAlign(RIGHT,BOTTOM);
          text(stats[4],xPos + textWidth(stats[4]) + 5,height - nMax );
          textAlign(LEFT,BOTTOM);
          text("Max: ",5,20);
          text(stats[4],50,20);
        }
      }
     
      if (stats.length > 5) {
        if (stats[5] != null) {
          stroke(0,128,0);
          fill(0,128,0);
          float Voltage = PApplet.parseFloat(stats[5]) * (10 / MAX_DAU) - 5;
          int sVoltage = PApplet.parseInt(map(truncate(Voltage),-5,+5,1,height));
          point(xPos,height - sVoltage);
          textAlign(RIGHT,BOTTOM);
          text(str(PApplet.parseInt(Voltage * MICRONPERVOLT * 1000)),xPos + textWidth(str(PApplet.parseInt(Voltage * MICRONPERVOLT * 1000))) + 5,height - sVoltage);  // * 1000 to convert to nM
          textAlign(LEFT,BOTTOM);
          text("D/A: ",5,80);
          text(str(PApplet.parseInt(Voltage * MICRONPERVOLT * 1000)),50,80); // * 1000 to convert to nM
        }
      }
      
      if (stats.length > 7) {
        if (stats[7] != null) {
          fDAU = PApplet.parseFloat(stats[7]); 
          textAlign(LEFT,BOTTOM);
          text("Mark: ", 5, 160);
          text(str(PApplet.parseInt(truncate(fDiffMark))), 50, 160);
        }
      }
     
      if (stats.length > 8) {
        if (stats[8] != null) {        
          stroke(128,0,128);
          fill(128,0,128);
          fExposure = PApplet.parseFloat(stats[8]);
          fExposure = map(fExposure,1000,10000,10,height-10);
          if (fExposure < (height/2)) textAlign(RIGHT,BOTTOM);
          else textAlign(RIGHT,TOP); 
          point(xPos,constrain(height-fExposure,1, height));
          text(stats[8],xPos + textWidth(stats[8])+ 5,height - fExposure);
          textAlign(LEFT,BOTTOM);
          text("Exp: ",5,120);
          text(stats[8],50,120);
        } 
      }
      
      if (stats.length > 9) {
        if (stats[9] != null) {        
          stroke(128,128,128);
          fill(128,128,128);
          float fCurrentADC = (PApplet.parseFloat(stats[9])/DAUPERVOLT ) * MICRONPERVOLT * 1000; // * 1000 to convert to nM from microns
          int sCurrentADC = PApplet.parseInt(map(fCurrentADC,-20000,20000,10,height-10));
          if (sCurrentADC < (height/2)) textAlign(RIGHT,BOTTOM);
          else textAlign(RIGHT,TOP); 
          point(xPos,constrain(height-sCurrentADC,1, height));
          text(str(PApplet.parseInt(fCurrentADC)),xPos + textWidth(str(PApplet.parseInt(fCurrentADC)))+ 5,height - sCurrentADC);
          textAlign(LEFT,BOTTOM);
          text("A/D: ",5,140);
          text(str(PApplet.parseInt(fCurrentADC)),50,140);
        } 
      }
      
      if (stats.length > 10) {
        if (stats[10] != null) { 
          diffADC = PApplet.parseInt(stats[10]);
        }
      }
      
      image(kiwi,25,550,65,45.7f);
      
      if (xPos >= width) {
        xPos = 100;
        //background(0);
      }
      else xPos++;
    }
  }
  
  if (nKey == 3) {
    hideGUI();
    //for (int x = 0; x < regressionPoints; x++)
    //  point(map(fRegressionPoints[regressionPoints][0],0,MAX_DAU,width,1), map(fRegressionPoints[regressionPoints][1],0,127,height, 1));
  }
  if (nKey == 1) {
    pgFocus.write('l');
    delay(200); 
  }
  else if (nKey == 2) {
    delay(200); 
  }
  
}

public void sendLetter(char letter)
{
  int nOldKey = nKey;
  nKey = 0; 
  delay(25); // time for draw() to stop
  pgFocus.write(letter);
  delay(25);
  nKey = nOldKey;
}

public void keyPressed() {
  
  int nOldKey = nKey;
  
  switch (key) {

  case 'b': // Blink main blue LED
    sendLetter('b');
    break;
  case 'c':
    regressionPoints = 0;
    fResiduals = 0;
    fSlope = 0;
    fIntercept = 0;
    hideGUI();
    nKey = 3;
    background(255);
    strokeWeight(2);
    textSize(12);
    text("Calibrating...",20,20);  
    sendLetter('c');
    break;
  case 'd':
    sendLetter('d');
    break;
  case 'e':
    sendLetter('e');
    break;
  case 'f':
    sendLetter('f');
    break;
  case 'g': // Blink main green LED
    sendLetter('g');
    break;
  case 'h':
    //sendLetter('s');
    nKey = 0;
    help();
    break;  
  case 'l':
    if (nKey == 1) nKey = 0;
    else {
      nKey = 1;
      background (255);
      sendLetter('l');
    }
    break; 

  case 'm':
    //sendLetter('m');
    mark();
    break;
  case 'o':
    sendLetter('s');
    break;
  case 'r': // Blink main red LED
    sendLetter('r');
    break;
  case 's':
    save("pgFocus.tif"); 
    break;  
  case 'u': // Move Focus Up
    sendLetter('u');
    break;
  case 'v': // Main screen showing stats
    if (nKey == 2) {
      nKey = 0;
    }
    else {
      if (nKey == 1) background(255);
      nKey = 2;
      sendLetter('v');
    }

    break;
  case 32:
    nKey = 0;
    break; 
  }
  
}


// Called whenever there is something available to read
public void serialEvent(Serial port) {
  
  String inString = pgFocus.readStringUntil('\n');
  String[] arrayString;
  
  if (inString != null) {
    // trim off any whitespace:
    inString = trim(inString);
    arrayString = split(inString," ");
    if (arrayString[0].equals("LIGHT:")) {             // Light profile
      light = arrayString;
      //println(inString);  
    } else if (arrayString[0].equals("STATS:")) {      // Stats
      stats = arrayString; 
      //println(inString);
    } else if (arrayString[0].equals("CAL:")) {      // Calibration
      //println("Found calibration");
      strokeWeight(8);
      stroke(255,0,0);
      fill(255,0,0);
      textAlign(LEFT,CENTER);
      cal = arrayString;
      fRegressionPoints[regressionPoints][0] = PApplet.parseFloat(cal[1]);
      //fRegressionPoints[regressionPoints][0] = (2 * MAX_VOLT * float(cal[1])/MAX_DAU) + MIN_VOLT; // DAU
      fRegressionPoints[regressionPoints][1] = PApplet.parseFloat(cal[2]); // FOCUS
      point(map(fRegressionPoints[regressionPoints][0],0,(MAX_DAU - 1),1,width), map(fRegressionPoints[regressionPoints][1],20,107,height,1));
      text(str(truncate(((PApplet.parseFloat(cal[1]) * 2 * MAX_VOLT/MAX_DAU) + MIN_VOLT) * MICRONPERVOLT)) +" \u00b5M, "+str(truncate(PApplet.parseFloat(cal[2]))) +" pixels",map(fRegressionPoints[regressionPoints][0],0,(MAX_DAU - 1),1,width) + 10,map(fRegressionPoints[regressionPoints][1],20,107,height,1));
      regressionPoints++; 
      if (regressionPoints >= REGRESSIONPOINTS) regressionPoints = REGRESSIONPOINTS - 1;
      println(inString);
    } else if (inString.equals("INFO: Focus OFF")) { 
      bFocus = false; 
     //println(inString);
    } else if (inString.equals("INFO: Focus ON")) { 
      bFocus = true; 
      //println(inString);
    } else if (arrayString[0].equals("SLOPE:")) { 
      fSlope = PApplet.parseFloat(arrayString[1]);
      println(inString);
    } else if (arrayString[0].equals("INTERCEPT:")) { 
      fIntercept = PApplet.parseFloat(arrayString[1]);
      println(inString);
    } else if (arrayString[0].equals("RESIDUALS:")) { 
      fResiduals = PApplet.parseFloat(arrayString[1]);
      println(inString);
    } else if (arrayString[0].equals("DAU:")) { 
      fDAU = PApplet.parseFloat(arrayString[1]);
      println(inString);
    } else if (arrayString[0].equals("ERROR:")) {
      textSize(20);
      fill(255,0,0);
      textAlign(CENTER,CENTER);
      text(inString,width/2,height/2);
      textSize(12);
      if (nKey == 2) sendLetter('s');
    }
    else println(inString);
    
  
  }
  
  if (regressionPoints > 0) {
    // Did we get these values from the hardware? If so, lets recompute them due to the fact that the hardware can't compute the residuals accurately
    // since it swaps the variables to avoid overflow
    if (fIntercept != 0 && fSlope != 0 && fResiduals != 0) {
      if (nKey == 3) {    
        float fDet, fSumX = 0.0f, fSumXX = 0.0f, fSumY = 0.0f, fSumXY = 0.0f, fMeanError = 0.0f;
        for (int x = 0; x < regressionPoints; x++) {
           fSumX  += fRegressionPoints[x][0];
           fSumXX += fRegressionPoints[x][0] * fRegressionPoints[x][0];
           fSumXY += fRegressionPoints[x][0] * fRegressionPoints[x][1];
           fSumY  += fRegressionPoints[x][1];
        }
        fDet = (regressionPoints * fSumXX - fSumX * fSumX);
        fSlope = (regressionPoints * fSumXY - fSumX * fSumY) / fDet;
        fIntercept = (fSumXX * fSumY - fSumX * fSumXY)/ fDet;    
        
        fResiduals = 0.0f; // clear the residuals from the pgFocus Hardware
        fMeanError = 0.0f;
        for (int x = 0; x < regressionPoints; x++) {
          fMeanError += fMeanError + sq(fRegressionPoints[x][1] - fSumY/regressionPoints);
          fResiduals += fResiduals + sq(fRegressionPoints[x][1] - fSlope * fRegressionPoints[x][0] - fIntercept);
        }
        fResiduals = 1 - (fResiduals/fMeanError);
        
        fill(255);
        noStroke();
        textSize(12);
        rect(20,20,textWidth("Calibrating..."),20); // Clear text
        //rect(10,height-100,80,height-50);
        stroke(255,0,0);
        strokeWeight(2); 
        
        float X1 = map(fRegressionPoints[0][0],0,MAX_DAU,1,width);
        float X2 = map(fRegressionPoints[regressionPoints-1][0],0,MAX_DAU,1,width);
        float Y1 = map((fSlope*fRegressionPoints[0][0])+fIntercept,20,107,height,1);
        float Y2 = map((fSlope*fRegressionPoints[regressionPoints-1][0])+fIntercept,20,107,height,1);
        line(X1,Y1,X2,Y2);
        fill(255,0,0);
        text("DAU: " + str(fDAU),10,height-120);
        text("Slope: " + str(fSlope),10,height-100);
        text("Intercept: " + str(fIntercept),10,height-80);
        text("Residuals: " + str(fResiduals),10,height-60);
        nKey = 0;
      }
    }
  }
}

public void mark() {
   
  if (fFocus > 0) {
    
    fNewMark = fFocus * (fDAU / DAUPERVOLT ) * MICRONPERVOLT * 1000; // convert to nM
   
    if (fOldMark == 0.0f) fDiffMark = 0;
    else fDiffMark = fNewMark - fOldMark;
    
    println("Focus: " + fFocus+"nM");
    println("New Mark: " + truncate(fNewMark)+"nM");
    println("Old Mark: " + truncate(fOldMark)+"nM");
    println("Difference: " + truncate(fDiffMark)+"nM"); 
    fOldMark = fNewMark; 
  } 
}

public float truncate(float x){
  if ( x > 0 )
    return PApplet.parseFloat(floor(x * 100))/100;
  else
    return PApplet.parseFloat(ceil(x * 100))/100;
}

public float standard_deviation(float data[], int n) { 
  
  float mean=0.0f, sum_deviation=0.0f; 
  int i;
  
  for(i=0; i < n; i++) 
  {
     mean+=data[i]; 
  }
  
  mean=mean/n; 
  for(i=0; i<n;i++)
  {
    sum_deviation+=(data[i]-mean)*(data[i]-mean);
  }
  
  return sqrt(sum_deviation/n);
}


public void handleButtonEvents(GButton button, GEvent event) {
   
  if (button == guiFocus && event == GEvent.CLICKED) {
    sendLetter('f'); 
  }
  
  if (button == guiUp && event == GEvent.CLICKED) {
    sendLetter('u'); 
  }
  
  if (button == guiDown && event == GEvent.CLICKED) {
    sendLetter('d'); 
  }

  if (button == guiMark && event == GEvent.CLICKED) {
     mark();
  }  

}

  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "pgFocus" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
