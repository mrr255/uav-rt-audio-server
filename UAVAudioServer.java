

import java.io.*;
import java.io.FileOutputStream;
import java.net.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.sound.sampled.*;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.spi.AudioFileWriter;
import java.util.concurrent.locks.ReentrantLock;
public class UAVAudioServer
{
  //Declare main variables for cross Thread referencing
  private String[] args;
	private DataOutputStream outputStream; //Data out to client
  private TargetDataLine micLine;
	private AudioInputStream micStream; //Data in from Microphone
	private ByteArrayOutputStream archiveStream; //Data out to File
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
  private FileOutputStream fileStream;
  private final ReentrantLock streamLock;
  private final ReentrantLock saveLock;
  private byte[] streamArray;
  private ServerSocket commsSocket;
  private BufferedReader input;
  private PrintWriter out;
  private volatile boolean comReady = false;
  private CommThread comms;
  private RecordThread recs;
  private WriteThread writes;
  private SendThread sends;
  private String logPath;


  //main class, Houses a main method, and initializes the program variables
  public UAVAudioServer()
  {
    date= new java.util.Date(); //Get Current Time at startup
    format = new AudioFormat(48000, 16, 1, true, true); //Define format of the audio stream
    reconnecting = false; //First connection, so not reconnecting
    System.out.println("Init Done!");
    streamLock = new ReentrantLock();
    saveLock = new ReentrantLock();

    new ServerThread(); //Create thread to begin the server.
  }//end constructor



  public static void main(String[] args)
  {
  	args = args;
    new UAVAudioServer(); //All work is done in constructor above
  } // end of main




  private void updateTime()
  {
    print("Supposedly updating!");
    try{
    Runtime rt = Runtime.getRuntime(); // Runs Python script
    String[] commands = {"python2","timesync.py"};
    Process proc = rt.exec(commands);

    BufferedReader stdInput = new BufferedReader(new
     InputStreamReader(proc.getInputStream())); //Gets Standard output from python

    BufferedReader stdError = new BufferedReader(new
     InputStreamReader(proc.getErrorStream())); //Gets Standard Error from python

    // read the output from the command
    print("Connecting to PixHawk and Synching, Please Wait...:\n");
    String s = null;

    while ((s = stdError.readLine()) != null)
    {
      print(s);
    }
    print("Time Received from PixHawk:");
    while ((s = stdInput.readLine()) != null)
    {
        print(s);
    }
    //print("Fake Time!");
    //print("2017-07-21T14-14-20_211");
    // read any errors from the attempted command

    while ((s = stdError.readLine()) != null)
    {
      print(s);
    }
  }
  catch(Exception e)
  {
    e.printStackTrace();
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
           System.out.println("Connected");
           //ON CONNECT
           shouldStop = false;
           isConnected = true;
           if(reconnecting) // If connection was previously lost, just reattach the audio stream
           {
             comms = new CommThread(); //Create listener socket for GUI stop signal
             sends = new SendThread();
             outputStream = new DataOutputStream(client.getOutputStream());
           }
           else //Otherwise, Initialize
           {
             comReady = false;
             comms = new CommThread(); //Create listener socket for GUI stop signal
             while(!comReady){continue;}
             System.out.println("GO!");
             updateTime(); //Set the PI Time
             recs = new RecordThread(); //Start Thread for recording
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
     long archstartTime;
     long archstopTime;
     long streamstartTime;
     long streamstopTime;
     long readstartTime;
     long readstopTime;

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
        dateForm = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss_SSS"); //Format code for filename
        logString = dateForm.format(startTime) + ".log";
        logPath = "." + File.separator + "flightData" + File.separator;
        try
        {
          log = new File(logPath + logString); //Attempt to make log
          logWriter = new FileWriter(log); // Open log for writing
        }
        catch(Exception e)
        {//If folder doesn't exist, make it
          e.printStackTrace();
          new File("." + File.separator + "flightData").mkdirs();
          log = new File(logPath + logString); // Then create and open the log
          logWriter = new FileWriter(log);
        }


        logWriter.write(dateForm.format(startTime) +".dat "); //Write the firt file to the log
        logWriter.flush(); // Force the write so it isn't lost
        dstFile = new File("." + File.separator +  "flightData" + File.separator + dateForm.format(startTime) + ".dat"); //create first data file
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format); //Initialize Microphone
        micLine = (TargetDataLine) AudioSystem.getLine(info);
        micLine.open(format);


        micStream = new AudioInputStream(micLine); //Create Microphone Stream
        archiveStream = new ByteArrayOutputStream(48000*15*2+16000); //Create "Archive"Local Stream
        fileStream = new FileOutputStream(dstFile);
        outputStream = new DataOutputStream(client.getOutputStream()); //Create "Remote" Socket Stream
        audioData = new byte[16000];
        streamArray = null;
        micLine.start(); //Activate Microphone
        print("Starting!");
        writes = new WriteThread();
        sends = new SendThread();
        int count = 0; //Initialize counter for File Max Size
        readstartTime = System.currentTimeMillis();
        while ((micStream.read(audioData)) != -1 && !shouldStop) //While Microphone has audio data, and not closed,
                //Read Buffer, and store in array "audiodata"
        {   readstopTime = System.currentTimeMillis();
            if((readstopTime - readstartTime) > 255)print("Long ReadTime: "+ (readstopTime - readstartTime) + "ms");
            archstartTime = System.currentTimeMillis();
            saveLock.lock();
            archiveStream.write(audioData, 0, audioData.length); //Write Data from array to file
            saveLock.unlock();
            archstopTime = System.currentTimeMillis();
            //if((archstopTime - archstartTime) > 1)print("ArchiveBlockTime1: "+ (archstopTime - archstartTime));
            //IF NOT LOCKED
            if(!streamLock.isLocked())
            {
            streamstartTime = System.currentTimeMillis();
            streamLock.lock();
            try
            {
            streamArray = audioData.clone(); //Try to write array to the socket
            }
            finally
            {
              streamLock.unlock();
              streamstopTime = System.currentTimeMillis();
              //if((streamstopTime - streamstartTime) > 1)print("StreamBlockTime: "+ (streamstopTime - streamstartTime));
            }
            }
            /*
            count += audioData.length; //Either way, update count. (Represents current size of data file)
            if(count >= 48000*15*2) // If at defined max size, (Sample Rate * Seconds * Frame Size)
            {
              archiveStream.writeTo(fileStream); //Close current file
              archiveStream.reset();
              Date newTime = new Date();  //Get current time (for timestamp)
              logWriter.write(dateForm.format(newTime) +".dat "); //add new data file to the log
              logWriter.flush(); //Force Write
              dstFile = new File("." + File.separator + "flightData" + File.separator + dateForm.format(newTime) + ".dat"); //Create new data file
              fileStream = new FileOutputStream(dstFile); //Init Stream for new file
              count = 0; //Reset Counter
            }//end if
            */
            readstartTime = System.currentTimeMillis();
        }//end while (MAIN WRITE LOOP)

        //If closing server,
        //Close all streams and files
        logWriter.close();
        outputStream.flush();
        outputStream.close();
        archiveStream.writeTo(new FileOutputStream(dstFile)); //Close current file
        archiveStream.reset();
        fileStream.close();
        writes.interrupt();
        micLine.close();
        //Set server states
        isConnected = false;
        reconnecting = false;
        //Get name of the current log file
        String processlog = logString;
        new ServerThread(); //Start Thread to wait for new connection
        print("Processing Audio");
        new AudioProcess(new String[] {processlog,logPath}); //Combine data files into a wav file. (Class in Seperate File)
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


    private void print(String s)
    {

      try
      {
        out.println(">"+s);
        System.out.println("Sent to remote:" + s);
      }
      catch(Exception e)
      {
      System.out.println(s);
      }
    }

    // Thread to Listen on seperate socket connection for the stop signal
    private class CommThread extends Thread
    {
      CommThread()
       {
         super();
         start();
       }

       public void run()
       {
         try (ServerSocket commsSocket = new ServerSocket(6667)) //Open socket on port 6667
         {
           if (commsSocket.isBound()) // if successful
           {
               Socket socket = commsSocket.accept();
               input =
                   new BufferedReader(new InputStreamReader(socket.getInputStream()));
               out =
                   new PrintWriter(socket.getOutputStream(), true);
               comReady = true;
               System.out.println("Comms Ready");
               if(reconnecting) // If connection was previously lost, just reattach the audio stream
               {
                 print("Server Reconnected Successfully!");
               }
               String answer = "";
               int counter = 0;
                 while (isConnected) {
                     //print(counter);
                     try {
                         if(input.ready())
                         {
                         answer = input.readLine();
                         System.out.println("Received: "+answer);
                         if(answer.equals("COMMAND: QUIT"))
                         {
                           print("Got Signal, Restarting Server!");
                           shouldStop = true; //Set boolean to exit main record loop
                           System.out.println(shouldStop);
                           out = null;
                           break;
                         }
                         }

                     }
                     finally
                    {}
             }
             print("commsSocket closed.");
             //serverSocket.accept(); //Wait for "stop signal" from client (Sonnection on this socket means stop)
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

     // Thread to Handle audio writing
     private class WriteThread extends Thread
     {
       long archstartTime;
       long archstopTime;
       long writestartTime;
       long writestopTime;
       WriteThread()
        {
          super();
          start();
        }

        public void run()
        {
          try{
            while(true)
            {
          Thread.sleep(10000);
          if(shouldStop)
          {
            System.out.println("Stop Writing!");
            shouldStop = false;
            break;
          }
          byte[] data;
          archstartTime = System.currentTimeMillis();
          saveLock.lock();
          data = archiveStream.toByteArray();
          archiveStream.reset();
          saveLock.unlock();
          archstopTime = System.currentTimeMillis();
          new FileOutputStream(dstFile).write(data); //Close current file

          Date newTime = new Date();  //Get current time (for timestamp)
          writestartTime = System.currentTimeMillis();
          logWriter.write(dateForm.format(newTime) +".dat "); //add new data file to the log
          logWriter.flush(); //Force Write
          writestopTime = System.currentTimeMillis();
          dstFile = new File("." + File.separator + "flightData" + File.separator + dateForm.format(newTime) + ".dat"); //Create new data file
          //if((archstopTime - archstartTime) > 1)print("ArchiveBlockTime2: "+ (archstopTime - archstartTime));
          if((writestopTime - writestartTime) > 2)print("Long WriteTime: "+ (writestopTime - writestartTime)+ "ms");
        }
        }
        catch(InterruptedException e)
        {
          System.out.println("Stop Writing, Interrupted!");
          shouldStop = false;
          return;
        }
        catch(Exception e)
        {
          e.printStackTrace();
        }
        }
      }//end of inner class

      private class SendThread extends Thread
      {
        long startTime;
        long stopTime;
        SendThread()
         {
           super();
           start();
         }

         public void run()
         {
           while(isConnected)
           {
             try{Thread.sleep(100);}
             catch(Exception e){e.printStackTrace();}

             if(streamArray == null)
             {
               //print("nullData");
               continue;
             }
             else
             {
               startTime = System.currentTimeMillis();
           streamLock.lock();
           try
           {
           outputStream.write(streamArray, 0, streamArray.length); //Try to write array to the socket

           streamArray = null;
           }
           catch(SocketException e)
           {
             //If Unsuccessful:
             if(isConnected) //and it was the first unsuccessful attempt
             {
               print("Just Lost Connection.");
               isConnected = false;
               reconnecting = true;
               new ServerThread(); //Reopen Server to look for connection

             }
             else
             { // No Connection, Do nothing.
               print("No Connection.");
             }
           }//end of catch
           catch (IOException e)
           {
            e.printStackTrace();
           }//end of catch
           finally
           {
           streamLock.unlock();
           stopTime = System.currentTimeMillis();
           if((stopTime - startTime) > 1)print("Long SendTime: "+ (stopTime - startTime)+"ms");
           }
         }
         }
       }
       }//end of inner class
} //end of class


//Solution Ideas
//Decrease Sample Rate
//Thread priority
//Size of frame as it comes in
//Diagnostics
//SOcket TO exception to ensure Stop thread doesn't interfere
//SLeep threads to tae turns
//Reference to threads
/*
System.currentTimeMillis()
System.nanoTime()
connect, try to connect, returns connect state
in main execution loop, check socketstate
sleep
*/
