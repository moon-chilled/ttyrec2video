/*
 */

package net.elronnd.ttyrec2video;

import java.awt.RenderingHints;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.cli.*;

/**
 * The application's main frame.
 */
@SuppressWarnings("serial")
public class MainFrame {
	public MainFrame(InputStreamable in, String out) {
		this(in, out, 1080);
	}

	/**
	 * Creates a new main window for the Jettyplay application.
	 */
	public MainFrame(InputStreamable in, String out, int size) {
		System.out.print("Loading...");
		// set no file to be open
		currentSource = null;

		if (in == null) return;

		openSourceFromInputStreamable(in);

		if ((currentSource.getTtyrec() == null)) {
			System.out.println("\nUnknown error loading file.  Exiting.");
			System.exit(2);
		}

		while (currentSource.backportDecodeProgress() == 0) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				System.out.println("\nInterrupted.  Exiting.");
				System.exit(3);
			}
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

		new SaveVideo(currentSource.getTtyrec(), out, size);
		System.out.println("done!");
		System.exit(0);
	}

	private void unloadFile() {
		if (getCurrentSource() != null) getCurrentSource().completeCancel();
		currentSource = null;
		VDUBuffer.resetCaches();
	}

	private void openSourceFromInputStreamable(InputStreamable iStream) {
		unloadFile();
		currentSource = new InputStreamTtyrecSource(iStream);
		getCurrentSource().completeUnpause();
		getCurrentSource().addDecodeListener(() -> {});
		getCurrentSource().start();
		previousFrameIndex = 0;
	}

	private TtyrecSource currentSource;

	private int previousFrameIndex = -1;

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

	/**
	 * The main entry point for the Jettyplay application.
	 * Parses and applies the effects of command-line arguments; if the
	 * arguments did not request an immediate exit, also creates a new main
	 * window for the Jettyplay application GUI and shows it.
	 * @param args The command-line arguments to parse.
	 */
	public static void main(String[] args) throws FileNotFoundException, ParseException {
		String out = null;
		InputStreamable strim = null;

		int height = 1080;


		Options options = new Options();
		options.addOption("h", false, "Height, in pixels [1080]");
		options.addOption("in", false, "Input file");
		options.addOption("out", false, "Output file");
		options.addOption("bucket", false, "ID of an aws S3 bucket");
		options.addOption("key", false, "Key for an aws S3 object");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		if (cmd.getOptionValue("h") != null) {
			try {
				height = Integer.parseInt(cmd.getOptionValue("h"));
			} catch (NumberFormatException e) {
				System.out.println("Height must be a number.");
				System.exit(1);
			}
		}

		boolean hasinput = cmd.getOptionValue("in") != null;
		boolean hass3 = (cmd.getOptionValue("bucket") != null) && (cmd.getOptionValue("key") != null);

		if (hasinput && hass3) {
			System.out.println("Error -- can't read from both a file and s3");
		} else if (hasinput) {
			strim = new InputStreamableFileWrapper(new File(cmd.getOptionValue("in")));
		} else if (hass3) {
			strim = new InputStreamableS3(cmd.getOptionValue("bucket"), cmd.getOptionValue("key"));
		} else {
			System.out.println("No input file or s3 bucket specified.");
			System.exit(1);
		}

		if (cmd.getOptionValue("out") == null) {
			System.out.println("At the moment, and output file is required.");
			System.exit(1);
		} else {
			out = cmd.getOptionValue("out");
		}


		new MainFrame(strim, out, height);
	}
}
