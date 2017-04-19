/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * SaveAsVideoDialog.java
 *
 * Created on 17-Dec-2012, 05:21:28
 */
package ttyrec2avi;

import java.awt.RenderingHints;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;

/**
 *
 * @author ais523
 */
@SuppressWarnings("serial")
public class SaveAsVideoDialog {
	private final Ttyrec ttyrec;

	private VideoContainer encodingContainer;
	String outfilename;

	/** Creates a new form to save a ttyrec as video
	 * @param parent The MainFrame that created this dialog box.
	 * @param ttyrec The ttyrec to save as a video. 
	 */
	public SaveAsVideoDialog(Ttyrec ttyrec, String outfilename, int size) {
		this.ttyrec = ttyrec;
		this.outfilename = outfilename;
		this.encodingContainer = null;
		doshit(size);
	}

	private void doshit(int size) {
		String fontName = "DejaVu Sans Mono";

		final VideoCodec[] codecs = {
			new ZMBVVideoCodec(size, new Font(fontName,Font.PLAIN,11),
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
					true),
			new RawVideoCodec(size, new Font(fontName,Font.PLAIN,11),
					 RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
					      true)};

			encodingContainer = new AVIVideoContainer();

		/*        Runnable videoEncodeThread = new Runnable() {

			  public void run() {*/
		try {
			ttyrec.encodeVideo(encodingContainer,
					codecs[0], // ZMBV, which is compressed
					//linearSpeedButton.isSelected() ? 
					new FrameTimeConvertor() {

					public double getFrameRate() {
					return 30.0;
					}

					public void resetConvertor() {
					}

					public int convertFrameTime(double frameTime) {
					return (int) (frameTime * 30);
					}
					} /*: logSpeedButton.isSelected() ? 
					    new FrameTimeConvertor() {

					    private double lastFrameTime = 0;
					    private double adjustedLastFrameTime = 0;

					    public double getFrameRate() {
					    return 30.0;
					    }

					    public void resetConvertor() {
					    lastFrameTime = 0;
					    adjustedLastFrameTime = 0;
					    }

					    public int convertFrameTime(double frameTime) {
					    if (frameTime - lastFrameTime > 1) {
					    adjustedLastFrameTime +=
					    1 + Math.log(frameTime - lastFrameTime);
					    } else {
					    adjustedLastFrameTime += frameTime - lastFrameTime;
					    }
					    lastFrameTime = frameTime;
					    return (int) (adjustedLastFrameTime * 30);

					    }
					    } : new FrameTimeConvertor() {

					    int frameNumber = 0;

					    public double getFrameRate() {
					    return fixedFramerate;
					    }

					    public void resetConvertor() {
					    frameNumber = 0;
					    }

					    public int convertFrameTime(double frameTime) {
					    return frameNumber++;
					    }
					    }*/);
			File f = new File(outfilename);
			try (OutputStream os = new FileOutputStream(f)) {
				encodingContainer.outputEncode(os);
			} catch(IOException ex) {
				System.out.print("Could not save file:" + outfilename);
			}
		} catch (CancellationException e) {
			// nothing to do
		} finally {
			encodingContainer = null; // make sure it doesn't leak
		}
		/*    }
		      };*/
		//	videoEncodeThread.run();
		//        new Thread(videoEncodeThread).start();
	}
}
