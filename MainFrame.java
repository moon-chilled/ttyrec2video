/*
 */

package ttyrec2avi;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.net.URISyntaxException;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.AttributedString;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * The application's main frame.
 */
@SuppressWarnings("serial")
public class MainFrame {

	public MainFrame(String in, String out) {
		this(in, out, 1080);
	}

	/**
	 * Creates a new main window for the Jettyplay application.
	 */
	public MainFrame(String in, String out, int size) {
		System.out.print("Loading...");
		// set no file to be open
		currentSource = null;

		InputStreamable iStream = null;

		File f = new File(in);
		iStream = new InputStreamableFileWrapper(f);

		if (iStream == null) return;

		openSourceFromInputStreamable(iStream);

		if ((currentSource.getTtyrec() == null)) {
			System.out.println("\nUnknown error loading file.  Exiting.");
			System.exit(2);
		}

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			System.out.println("\nInterrupted.  Exiting.");
			System.exit(3);
		}

		while (currentSource.backportDecodeProgress() < currentSource.getTtyrec().getFrameCount()) {
			System.out.print(".");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println("\nInterrupted.  Exiting.");
				System.exit(3);
			}
		}

		System.out.println("done!");

		System.out.print("Saving...");

		new SaveAsVideoDialog(currentSource.getTtyrec(), out, size);
		System.out.println("done!");
		System.exit(0);
	}

	private void setTtyrecFormat(Ttyrec.Encoding format) {
		if (getCurrentTtyrec() == null) return;
		if (getCurrentTtyrec().getEncoding() == format) return;
		getCurrentTtyrec().setEncoding(format);
		getCurrentSource().repeatCurrentDecodeWorker();
	}

	private String timeToString(double time) {
		int t = (int)time;
		if (t<0) t = 0;
		String s = (t/60) + ":";
		if (t > 3600) {
			s = (t/3600) + ":";
			if ((t/60)%60 < 10) s += "0";
			s += (t/60)%60 + ":";
		}
		if (t%60 < 10) s += "0";
		s += t%60;
		return s;
	}

	private void unloadFile() {
		playing = false;
		if (getCurrentSource() != null) getCurrentSource().completeCancel();
		currentSource = null;
		VDUBuffer.resetCaches();
	}

	private void openSourceFromInputStreamable(InputStreamable iStream) {
		unloadFile();
		currentSource = new InputStreamTtyrecSource(iStream);
		getCurrentSource().completeUnpause();
		getCurrentSource().addDecodeListener(new ProgressListener() {
			public void progressMade() {
			}
		});
		getCurrentSource().start();
		previousFrameIndex = 0;
	}

	private TtyrecSource currentSource;

	// TODO: Reduce this in the case of excessively time-long ttyrecs,
	// to avoid an integer overflow
	private int timeScaling = 1000;
	private boolean playing = false;
	private int previousFrameIndex = -1;
	private long timeStartedAt;
	private int sliderValueStartedAt;
	private boolean canUpdateTimeStartedAt = true;
	private boolean canUpdateSelectedFrame = true;

	private File lastDirectory = null;

	public double getMaximumTime() {
		if (getCurrentTtyrec() == null) return 0.0;
		return getCurrentTtyrec().getLength();
	}
	private TtyrecFrame getCurrentFrame() {
		try {
			return getCurrentTtyrec().getFrameAtIndex(previousFrameIndex);
		} catch(IndexOutOfBoundsException ex) {
			return null;
		}
	}

	/**
	 * Searches for a given string in the currently open ttyrec; if it's found,
	 * then seeks the current ttyrec to the frame where it was found.
	 * @param searchFor The string to search for.
	 * @param searchForward Whether to search forwards (true) or backwards (false).
	 * @param regex Whether the string to search for is actually a regex.
	 * @param ignoreCase Whether to do a case-insensitive (true) or case-sensitive (false) search.
	 * @param wrapAround Whether to restart the search at one end of the ttyrec if it's finished at the other end.
	 * @return A string that can be displayed to the user, summarising the results of the search.
	 */
	public String searchInTtyrec(String searchFor, boolean searchForward,
			boolean regex, boolean ignoreCase, boolean wrapAround) {
		Pattern p;
		try {
			// Regex.LITERAL would be nice, but it's too new. So we quote the
			// regex by hand, according to Perl 5 quoting rules; all letters
			// and all digits are left as-is, other characters are preceded by
			// a backslash.
			if (!regex) {
				StringBuilder sb = new StringBuilder();
				for (char c: searchFor.toCharArray()) {
					if (!Character.isLetter(c) && !Character.isDigit(c))
						sb.append('\\');
					sb.append(c);
				}
				searchFor = sb.toString();
			}
			p = Pattern.compile(searchFor, (ignoreCase ? Pattern.CASE_INSENSITIVE : 0));
		} catch (PatternSyntaxException e) {
			return "Invalid regular expression.";
		}
		for (int i = previousFrameIndex;
				i < getCurrentTtyrec().getFrameCount() && i >= 0; i += searchForward ? 1 : -1) {
			if (i == previousFrameIndex) {
				continue;
			}
			if (getCurrentTtyrec().getFrameAtIndex(i).containsPattern(p)) {
				return "Found at frame " + i + ".";
			}
		}
		if (wrapAround) {
			for (int i = searchForward ? 0 : getCurrentTtyrec().getFrameCount() - 1;
					i != previousFrameIndex;
					i += searchForward ? 1 : -1) {
				if (getCurrentTtyrec().getFrameAtIndex(i).containsPattern(p)) {
					return "Found at frame " + i + " (wrapped).";
				}
			}
		}
		return "Match not found.";
	}

	/**
	 * Gets the currently visible ttyrec source; that's the selected source from
	 * the playlist.
	 * @return the currently selected ttyrec source.
	 */
	private TtyrecSource getCurrentSource() {
		return currentSource;
	}


	/**
	 * Returns the currently viewed ttyrec.
	 * Even if more than one ttyrec is open, only the one currently showing is
	 * returned.
	 * @return The current ttyrec, or null if there are no open ttyrecs.
	 */
	public Ttyrec getCurrentTtyrec() {
		if (getCurrentSource() == null) return null;
		return getCurrentSource().getTtyrec();
	}

	// This method exists to avoid causing problems with missing fields
	// in early JDK versions.
	private Object safelyGetRenderingHint(String hintName) {
		try {
			return RenderingHints.class.getField(hintName).get(null);
		} catch(NoSuchFieldException | SecurityException |
				IllegalArgumentException | IllegalAccessException e) {
			return RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
		}
	}

	/**
	 * The main entry point for the Jettyplay application.
	 * Parses and applies the effects of command-line arguments; if the
	 * arguments did not request an immediate exit, also creates a new main
	 * window for the Jettyplay application GUI and shows it.
	 * @param args The command-line arguments to parse.
	 */
	public static void main(String[] args) {
		if ((args.length != 2) && (args.length != 4)) {
			System.out.println("Usage: java [-server] -jar [-h height] <filename.jar> <infile.ttyrec> <outfile.avi>");
			System.exit(1);
		}

		boolean setheight = false;
		String in = null, out = null;
		int height = -1;

		if (args.length == 2) {
			new MainFrame(args[0], args[1]);
		} else {
			for (String s: args) {
				if (setheight) {
					try {
						height = Integer.parseInt(s);
					} catch (NumberFormatException e) {
						System.out.println("Height must be a number.");
						System.exit(1);
					}
					setheight = false;
					continue;
				}

				if (s.equals("-h")) {
					if (height != -1) {
						System.out.println("Height can only be set once.");
						System.exit(1);
					} else {
						setheight = true;
						continue;
					}
				}

				// Definitely a file
				if (in == null) {
					in = s;
				} else if (out == null) {
					out = s;
				} else {
					System.out.println("Too many files!");
					System.exit(1);
				}
			}

			if ((in == null) || (out == null)) {
				System.out.println("Not enough files!");
				System.exit(1);
			}

			new MainFrame(in, out, height);
		}
	}
}
