
import java.io.*;
import java.net.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.spi.AudioFileWriter;
import javax.sound.sampled.*;

import java.sql.Timestamp;
import java.util.Date;
import java.text.SimpleDateFormat;

public class UAVAudioServer
{
  //Declare main variables for cross Thread referencing
  private String[] args;
	private DataOutputStream outputStream; //Data out to client
  private TargetDataLine micLine;
	private AudioInputStream micStream; //Data in from Microphone
	private FileOutputStream archiveStream; //Data out to File
	private FileWriter logWriter; //Data out to Log
  private File log; //Log File
  private String logString; //Name of log file
	private byte[] audioData; //Array to hold incoming Audio data
  private File dstFile; // archive "destination" file
  private java.util.Date date; //Start time of program
  private AudioFormat format;
  private Date startTime; //Start time used for log and first data file
  private SimpleDateFormat dateForm;
  private Socket client; //Remote end of Socket for main client connection
  private boolean isConnected;
  private boolean reconnecting;
  private boolean shouldStop;
  private ServerSocket mainServerSocket; //Local end for server Socket




  //main class, Houses a main method, and initializes the program variables
  public UAVAudioServer()
  {
    date= new java.util.Date(); //Get Current Time at startup
    format = new AudioFormat(48000, 16, 1, true, true); //Define format of the audio stream
    reconnecting = false; //First connection, so not reconnecting
    System.out.println("Init Done!");

    new ServerThread(); //Create thread to begin the server.
  }//end constructor



  public static void main(String[] args)
  {
  	args = args;
    new UAVAudioServer(); //All work is done in constructor above
  } // end of main




  private void updateTime()
  {
    System.out.println("Supposedly updating!");
    try{
    Runtime rt = Runtime.getRuntime(); // Runs Python script
    String[] commands = {"python","timesync.py"};
    Process proc = rt.exec(commands);

    BufferedReader stdInput = new BufferedReader(new
     InputStreamReader(proc.getInputStream())); //Gets Standard output from python

    BufferedReader stdError = new BufferedReader(new
     InputStreamReader(proc.getErrorStream())); //Gets Standard Error from python

    // read the output from the command
    System.out.println("Here is the standard output of the command:\n");
    String s = null;
    while ((s = stdInput.readLine()) != null)
    {
        System.out.println(s);
    }

    // read any errors from the attempted command
    System.out.println("Here is the standard error of the command (if any):\n");
    while ((s = stdError.readLine()) != null)
    {
      System.out.println(s);
    }
  }
  catch(Exception e)
  {
    System.out.println(e);
  }
  }



  //Thread that creates main connection for audio transmission and recording.
  private class ServerThread extends Thread
  {
    ServerThread()
     {
       super();
       start();
     }

     public void run()
     {
       try (ServerSocket mainServerSocket = new ServerSocket(6666)) //Create Audio server on socket 6666
       {
         if (mainServerSocket.isBound()) // if setup is successful
         {
           System.out.println("Server Waiting!");
           client = mainServerSocket.accept(); //WAIT for connection from client

           //ON CONNECT
           shouldStop = false;
           isConnected = true;
           if(reconnecting) // If connection was previously lost, just reattach the audio stream
           {
             outputStream = new DataOutputStream(client.getOutputStream());
           }
           else //Otherwise, Initialize
           {
             updateTime(); //Set the PI Time
             new StopThread(); //Create listener socket for GUI stop signal
             new RecordThread(); //Start Thread for recording
           }
         }//end of if
       }//end of try
       catch (SocketException e)
       {
        e.printStackTrace();
       }
       catch (IOException e)
       {
         e.printStackTrace();
       }//end of catch
     }//end of run
   }//end of inner class



   //Thread for Performing recording of audio whether or not client is connected.
   private class RecordThread extends Thread
   {
     RecordThread()
      {
        super();
        start();
      }

      public void run()
      {
        try
        {
        micLine = AudioSystem.getTargetDataLine(format); //Prep Microphone
        startTime = new Date(); //Get current time  (BASE FOR TIMESTAMP)
        dateForm = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"); //Format code for filename
        logString = "." + File.separator + "flightData" + File.separator + dateForm.format(startTime) + ".log";
        try
        {
          log = new File(logString); //Attempt to make log
          logWriter = new FileWriter(log); // Open log for writing
        }
        catch(Exception e)
        {//If folder doesn't exist, make it
          System.out.println(e);
          new File("." + File.separator + "flightData").mkdirs();
          log = new File(logString); // Then create and open the log
          logWriter = new FileWriter(log);
        }


        logWriter.write(dateForm.format(startTime) +".dat "); //Write the firt file to the log
        logWriter.flush(); // Force the write so it isn't lost
        dstFile = new File("." + File.separator +  "flightData" + File.separator + dateForm.format(startTime) + ".dat"); //create first data file
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format); //Initialize Microphone
        micLine = (TargetDataLine) AudioSystem.getLine(info);
        micLine.open(format);


        micStream = new AudioInputStream(micLine); //Create Microphone Stream
        archiveStream = new FileOutputStream(dstFile); //Create "Archive"Local Stream
        outputStream = new DataOutputStream(client.getOutputStream()); //Create "Remote" Socket Stream
        audioData = new byte[16000];

        micLine.start(); //Activate Microphone
        System.out.println("Starting!");

        int count = 0; //Initialize counter for File Max Size
        while ((micStream.read(audioData)) != -1 && !shouldStop) //While Microphone has audio data, and not closed,
                //Read Buffer, and store in array "audiodata"
        {
            archiveStream.write(audioData, 0, audioData.length); //Write Data from array to file
            try
            {
            outputStream.write(audioData, 0, audioData.length); //Try to write array to the socket
            }
            catch(SocketException e)
            {
              //If Unsuccessful:
              if(isConnected) //and it was the first unsuccessful attempt
              {
                System.out.println("Just Lost Connection.");
                isConnected = false;
                reconnecting = true;
                new ServerThread(); //Reopen Server to look for connection
              }
              else
              { // No Connection, Do nothing.
                //System.out.println("No Connection.");
              }
            }//end of catch
            count += audioData.length; //Either way, update count. (Represents current size of data file)
            if(count >= 48000*15*2) // If at defined max size, (Sample Rate * Seconds * Frame Size)
            {
              archiveStream.close(); //Close current file
              Date newTime = new Date();  //Get current time (for timestamp)
              logWriter.write(dateForm.format(newTime) +".dat "); //add new data file to the log
              logWriter.flush(); //Force Write
              dstFile = new File("." + File.separator + "flightData" + File.separator + dateForm.format(newTime) + ".dat"); //Create new data file
              archiveStream = new FileOutputStream(dstFile); //Init Stream for new file
              count = 0; //Reset Counter
            }//end if
        }//end while (MAIN WRITE LOOP)

        //If closing server,
        //Close all streams and files
        logWriter.close();
        outputStream.flush();
        outputStream.close();
        archiveStream.close();
        micLine.close();
        //Set server states
        isConnected = false;
        reconnecting = false;
        shouldStop = false;
        //Get name of the current log file
        String processlog = logString;
        new ServerThread(); //Start Thread to wait for new connection
        System.out.println("Processing Audio");
        new AudioProcess(new String[] {processlog}); //Combine data files into a wav file. (Class in Seperate File)
      }//end of try
      catch (LineUnavailableException e)
      {
          e.printStackTrace();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }//end of catch
      }//end of run
    }//end of inner class


    // Thread to Listen on seperate socket connection for the stop signal
    private class StopThread extends Thread
    {
      StopThread()
       {
         super();
         start();
       }

       public void run()
       {
         try (ServerSocket serverSocket = new ServerSocket(6667)) //Open socket on port 6667
         {
           if (serverSocket.isBound()) // if successful
           {
             serverSocket.accept(); //Wait for "stop signal" from client (Sonnection on this socket means stop)
             System.out.println("Got Signal, Restarting Server!");
             shouldStop = true; //Set boolean to exit main record loop
           }//end of if
         }//end of try
         catch (SocketException e)
         {
          e.printStackTrace();
         }
         catch (IOException e)
         {
          e.printStackTrace();
         }//end of catch
       }//end of run
     }//end of inner class

} //end of class


//Solution Ideas:
//Decrease Sample Rate
//Thread priority
//Size of frame as it comes in
//Diagnostics
//SOcket TO exception to ensure Stop thread doesn't interfere
//SLeep threads to tae turns
//Reference to threads98
