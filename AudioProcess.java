
import java.io.*;
import java.util.*;
import javax.sound.sampled.*;

public class AudioProcess
{

  public AudioProcess(String[] args)
  {
    if(args.length != 2)
    {
      System.out.println("error: need log file, and path to dat folder");
      return;
    }
    System.out.println("Creating Wav File...");
    String path = args[1];
    File log = new File(path + args[0]); // Log has list of data files

    try
    {
      Scanner logr = new Scanner(log);
      ByteArrayOutputStream bOut = new ByteArrayOutputStream();
      while(logr.hasNext())
      {	//Read Each File Name
        String curFName = logr.next();
        RandomAccessFile curF = new RandomAccessFile(path + File.separator + curFName, "r"); // Open Each File
        byte[] curB = new byte[(int)curF.length()]; //Make containing byte array
        curF.readFully(curB);
        bOut.write(curB); //Add to byte array
        curB = null;
	      curF.close();
        //Write existing Data and New data to the array
        //dat = concat(dat,curB);
      }
      byte[] dat = bOut.toByteArray();
      bOut.close();
	//Once Done, convert Byte Array to Wav File
      AudioInputStream pcm;
      InputStream b_in = new ByteArrayInputStream(dat);
      AudioFormat format = new AudioFormat(48000, 16, 1, true, true);
      AudioInputStream source = new AudioInputStream(b_in,format , dat.length/2);
      String file = args[0].substring(0,args[0].length()-4);
      File newFile = new File(path + File.separator +file + ".wav");
      AudioSystem.write(source, AudioFileFormat.Type.WAVE, newFile);
      System.out.println("Wav File Created Successfully");
      source.close();
      //pcm.close();
    }
    catch (FileNotFoundException e)
    {
      System.out.println("error: need valid file name");
      System.out.println(e);
      return;
    }
    catch (IOException e)
    {
      System.out.println("error: error reading dat file");
      System.out.println(e);
      return;
    }
  }
  public static byte[] concat(byte[] a, byte[] b) {
   int aLen = a.length;
   int bLen = b.length;
   byte[] c= new byte[aLen+bLen];
   System.arraycopy(a, 0, c, 0, aLen);
   System.arraycopy(b, 0, c, aLen, bLen);
   return c;
  }
  public static void main(String[] args)
  {
    new AudioProcess(args);

  }
}
