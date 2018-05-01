package mfi.files.htmlgen;

public enum KeyCodes {

	ESCAPE(27), //
	UP(38), //
	DOWN(40), //
	LEFT(37), //
	RIGHT(39), //
	ENTER(13), //
	F12(123), //
	;

	private KeyCodes(int code) {
		this.code = code;
	}

	private final static String PREFIX_METHOD_NAME = "press";

	private Integer code;

	public String methodDeclarator() {
		return PREFIX_METHOD_NAME + getCode().toString();
	}

	public Integer getCode() {
		return code;
	}

}
