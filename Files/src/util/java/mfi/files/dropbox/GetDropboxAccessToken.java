package mfi.files.dropbox;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuth;

public class GetDropboxAccessToken {

	public static void main(String[] args) throws Exception {

		System.out.println("Type appKey");
		String appKey = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
		System.out.println("Type appSecret");
		String appSecret = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

		DbxAppInfo appInfo = new DbxAppInfo(appKey, appSecret);

		DbxRequestConfig config = new DbxRequestConfig("Files");
		DbxWebAuth webAuth = new DbxWebAuth(config, appInfo);
		DbxWebAuth.Request request = DbxWebAuth.newRequestBuilder().withNoRedirect().build();

		String authorizeUrl = webAuth.authorize(request);
		System.out.println("1. Go to: " + authorizeUrl);
		System.out.println("2. Click \"Allow\" (you might have to log in first)");
		System.out.println("3. Copy the authorization code.");
		String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

		// This will fail if the user enters an invalid authorization code.
		DbxAuthFinish authFinish = webAuth.finishFromCode(code);
		System.out.println("Authorization complete.");
		System.out.println("getUserId  =" + authFinish.getUserId());
		System.out.println("accessToken=" + authFinish.getAccessToken());

	}
}
