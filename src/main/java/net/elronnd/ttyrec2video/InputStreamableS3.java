package net.elronnd.ttyrec2video;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;


public class InputStreamableS3 implements InputStreamable {
	final AmazonS3 s3;
	final S3Object object;

	public InputStreamableS3(String bucket, String key) throws FileNotFoundException {
		s3 = AmazonS3ClientBuilder.defaultClient();
		object = s3.getObject(bucket, key);
	}

	public InputStream getInputStream() {
		return object.getObjectContent();
	}

	public URI getURI() throws URISyntaxException {
		throw new URISyntaxException("Bla", "Don't support getting s3 file URIs yet");
	}

	public boolean isReadable() {
		return true;
	}

	public boolean isEOFPermanent() {
		// I don't know, and I don't want to risk it
		return false;
	}

	public long getLength() {
		return object.getObjectMetadata().getInstanceLength();
	}

	public boolean couldBeStreamable() {
		return true;
	}

	public boolean mustBeStreamable() {
		return true; // I think?
	}

	public void cancelIO() {
		try {
			object.getObjectContent().close();
		} catch (IOException ioe) {
		}
	}
}
