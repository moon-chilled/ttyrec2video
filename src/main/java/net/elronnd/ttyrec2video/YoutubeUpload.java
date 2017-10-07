package net.elronnd.ttyrec2video;

import java.util.List;
import java.io.File;
import java.io.IOException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
//import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
//import com.google.api.client.util.store.DataStore;
//import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.common.collect.Lists;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;



class Auth {
	public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

	public static final JsonFactory JSON_FACTORY = new JacksonFactory();

	public static Credential authorize(String token) {
		Credential ret = new Credential(BearerToken.queryParameterAccessMethod());
		ret.setAccessToken(token);
		return ret;
	}
}



public class YoutubeUpload {

	private static YouTube youtube;


	YoutubeUpload(File file, String token, String title, String description) throws IOException {
		Credential credential = Auth.authorize(token);

		youtube = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, credential).setApplicationName("ttyrec2video.youtube").build();

		Video metadata = new Video();
		VideoStatus status = new VideoStatus();
		status.setPrivacyStatus("public");
		metadata.setStatus(status);

		VideoSnippet snippet = new VideoSnippet();
		snippet.setTitle(title);
		snippet.setDescription(description);

		metadata.setSnippet(snippet);


		InputStreamContent content = new InputStreamContent("video/*", file.getAbsoluteFile().toURL().openStream());
		YouTube.Videos.Insert videoinsert = youtube.videos().insert("snippet,statistics,status", metadata, content);

		MediaHttpUploader uploader = videoinsert.getMediaHttpUploader();

		uploader.setDirectUploadEnabled(false);

		Video returnedVideo = videoinsert.execute();

		System.out.println("https://youtu.be/" + returnedVideo.getId());
		System.exit(0);
	}
}
