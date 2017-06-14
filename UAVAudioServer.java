
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
  private String[] args;
	private DataOutputStream outputStream;
  private TargetDataLine micLine;
	private AudioInputStream micStream;
	private FileOutputStream archiveStream;
	private FileWriter logWriter;
  private File log;
  private String logString;
	private byte[] audioData;
  private File dstFile;
  private java.util.Date date;
  private AudioFormat format;
  private Date startTime;
  private SimpleDateFormat dateForm;
  private Socket client;
  private boolean isConnected;
  private boolean reconnecting;
  private boolean shouldStop;
  private ServerSocket mainServerSocket;

  public UAVAudioServer()
  {
    date= new java.util.Date();
    format = new AudioFormat(48000, 16, 1, true, true);
    reconnecting = false;
    System.out.println("Init Done!");
    new ServerThread();
  }//end constructor

  public static void main(String[] args)
  {
  	args = args;
    new UAVAudioServer();
  } // end of main

  private void updateTime()
  {
    System.out.println("Supposedly updating!");
    try{
    Runtime rt = Runtime.getRuntime();
    String[] commands = {"python","timesync.py"};
    Process proc = rt.exec(commands);

    BufferedReader stdInput = new BufferedReader(new
     InputStreamReader(proc.getInputStream()));

    BufferedReader stdError = new BufferedReader(new
     InputStreamReader(proc.getErrorStream()));

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
        //System.out.println(s);
    }
  }
  catch(Exception e)
  {
    System.out.println(e);
  }
  }
  private class ServerThread extends Thread
  {
    ServerThread()
     {
       super();
       start();
     }

     public void run()
     {
       try (ServerSocket mainServerSocket = new ServerSocket(6666))
       {
         if (mainServerSocket.isBound())
         {
           System.out.println("Server Waiting!");
           client = mainServerSocket.accept();
           shouldStop = false;
           isConnected = true;
           if(reconnecting)
           {
             outputStream = new DataOutputStream(client.getOutputStream());
           }
           else
           {
             updateTime();
             new StopThread();
             new RecordThread();
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
        micLine = AudioSystem.getTargetDataLine(format);
        startTime = new Date();
        dateForm = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        try
        {
          log = new File("." + File.separator + "flightData" + File.separator + dateForm.format(startTime) + ".log");
          logWriter = new FileWriter(log);
        }
        catch(Exception e)
        {//If folder doesn't exist, make it
          System.out.println(e);
          new File("." + File.separator + "flightData").mkdirs();
        }
        logString = "." + File.separator + "flightData" + File.separator + dateForm.format(startTime) + ".log";
        log = new File(logString);
        logWriter = new FileWriter(log);
        logWriter.write(dateForm.format(startTime) +".dat ");
        logWriter.flush();
        dstFile = new File("." + File.separator +  "flightData" + File.separator + dateForm.format(startTime) + ".dat");
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        micLine = (TargetDataLine) AudioSystem.getLine(info);
        micLine.open(format);
        micStream = new AudioInputStream(micLine);
        archiveStream = new FileOutputStream(dstFile);
        outputStream = new DataOutputStream(client.getOutputStream());
        audioData = new byte[16000];

        micLine.start();
        System.out.println("Starting!");
        int count = 0;
        while ((micStream.read(audioData)) != -1 && !shouldStop)
        {
          //numBytesRead = microphoneStream.read(data);
            //bytesRead += numBytesRead;
            // write the mic data to a stream for use later
            archiveStream.write(audioData, 0, audioData.length);
            try
            {
            outputStream.write(audioData, 0, audioData.length);
            }
            catch(SocketException e)
            {
              if(isConnected)
              {
                System.out.println("Just Lost Connection.");
                isConnected = false;
                reconnecting = true;
                new ServerThread();
              }
              else
              {
                //System.out.println("No Connection.");
              }
            }//end of catch
            count += audioData.length;
            if(count >= 48000*10*2)
            {
              archiveStream.close();
              Date newTime = new Date();
              logWriter.write(dateForm.format(newTime) +".dat ");
              logWriter.flush();
              dstFile = new File("." + File.separator + "flightData" + File.separator + dateForm.format(newTime) + ".dat");
              archiveStream = new FileOutputStream(dstFile);
              count = 0;
            }
        }
        logWriter.close();
        outputStream.flush();
        outputStream.close();
        archiveStream.close();
        micLine.close();
        isConnected = false;
        reconnecting = false;
        shouldStop = false;
        String processlog = logString;

        new ServerThread();
        System.out.println("Processing Audio");
        new AudioProcess(new String[] {processlog});
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



    private class StopThread extends Thread
    {
      StopThread()
       {
         super();
         start();
       }

       public void run()
       {
         try (ServerSocket serverSocket = new ServerSocket(6667))
         {
           if (serverSocket.isBound())
           {
             serverSocket.accept();
             System.out.println("Got Signal, Restarting Server!");
             shouldStop = true;
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
