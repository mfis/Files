package mfi.files.model;

import java.io.Serializable;

public class CheckTokenResult implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean checkOk;

	private final String newToken;

	public CheckTokenResult(boolean checkOK, String newToken) {
		super();
		this.checkOk = checkOK;
		this.newToken = newToken;
	}

	public boolean isCheckOk() {
		return checkOk;
	}

	public String getNewToken() {
		return newToken;
	}

}
