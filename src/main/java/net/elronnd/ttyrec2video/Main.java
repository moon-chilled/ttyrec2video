/*
 */

package net.elronnd.ttyrec2video;

import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;


/**
 * The application's main frame.
 */
@SuppressWarnings("serial")
public class Main {
	private static void error(String msg, int code) {
		System.out.println("\n" + msg);
		System.exit(code);
	}

	public Main(InputStreamable in, String out, int size, boolean shouldexit) {
		System.out.print("Loading...");
		// set no file to be open
		currentSource = null;

		if (in == null) return;

		openSourceFromInputStreamable(in);

		if ((currentSource.getTtyrec() == null)) {
			error("Unknown error loading file.  Exiting.", 2);
		}

		while (currentSource.backportDecodeProgress() == 0) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				error("Interrupted.  Exiting.", 3);
			}
		}

		while (currentSource.backportDecodeProgress() < currentSource.getTtyrec().getFrameCount()) {
			System.out.print(".");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				error("Interrupted.  Exiting.", 3);
			}
		}

		System.out.println("done!");

		System.out.print("Saving...");

		new SaveVideo(currentSource.getTtyrec(), out, size);
		System.out.println("done!");

		if (shouldexit) {
			System.exit(0);
		}
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


	/**
	 * Gets the currently visible ttyrec source; that's the selected source from
	 * the playlist.
	 * @return the currently selected ttyrec source.
	 */
	private TtyrecSource getCurrentSource() {
		return currentSource;
	}


	/**
	 * The main entry point for the Jettyplay application.
	 * Parses and applies the effects of command-line arguments; if the
	 * arguments did not request an immediate exit, also creates a new main
	 * window for the Jettyplay application GUI and shows it.
	 * @param args The command-line arguments to parse.
	 */
	public static void main(String[] args) throws FileNotFoundException, ParseException, IOException {
		String out = null;
		InputStreamable strim = null;

		int height = 1080;


		Options options = new Options();
		options.addOption("h", true, "Height, in pixels [1080]");
		options.addOption("in", true, "Input file");
		options.addOption("out", true, "Output file");
		options.addOption("s3bucket", true, "ID of an aws S3 bucket");
		options.addOption("s3key", true, "Key for an aws S3 object");
		options.addOption("yttitle", true, "Title for a youtube upload");
		options.addOption("ytdescr", true, "Youtube video description");
		options.addOption("yttoken", true, "Authorization token for youtube");
		options.addOption("help", false, "Show help");


		CommandLine cmd = new DefaultParser().parse(options, args);

		if (cmd.hasOption("help")) {
			System.out.println("Useage: java [-server] -jar ttyrec2video.jar [-h height] [-in input file] [-s3bucket s3 bucket -s3key s3 object key] -out <output file> [-yttitle youtube title -ytdescr youtube video description -yttoken OAuth2 token for use with youtube]");
			System.exit(0);
		}

		if (cmd.getOptionValue("h") != null) {
			try {
				height = Integer.parseInt(cmd.getOptionValue("h"));
			} catch (NumberFormatException e) {
				System.out.println("Height must be a number.");
				System.exit(1);
			}
		}

		boolean hasinput = cmd.hasOption("in");
		boolean hass3 = cmd.hasOption("s3bucket") && cmd.hasOption("s3key");

		if (hasinput && hass3) {
			error("Error -- can't read from both a file and s3", 1);
		} else if (hasinput) {
			strim = new InputStreamableFileWrapper(new File(cmd.getOptionValue("in")));
		} else if (hass3) {
			strim = new InputStreamableS3(cmd.getOptionValue("s3bucket"), cmd.getOptionValue("s3key"));
		} else {
			error("No input file or s3 bucket specified.", 1);
		}

		out = cmd.hasOption("out") ? cmd.getOptionValue("out") : cmd.hasOption("in") ? cmd.getOptionValue("in") + ".avi" : "out.avi";

		boolean hasyt = cmd.hasOption("yttitle") && cmd.hasOption("yttoken") && cmd.hasOption("ytdescr");

		new Main(strim, out, height, !hasyt);

		if (hasyt) {
			new YoutubeUpload(new File(out), cmd.getOptionValue("yttoken"), cmd.getOptionValue("yttitle"), cmd.getOptionValue("ytdescr"));
		}
	}
}
