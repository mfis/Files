package mfi.files.model;

public enum Condition {

	NULL(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.NO), //
	LOGIN(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.YES), //
	LOGIN_FORMULAR(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.YES), //
	LOGOFF(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_BEFORE, StepBack.NO), //
	AUTOLOGIN_FROM_COOKIE(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.NO), //
	LOGIN_GENERATE_CREDENTIALS(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.NO), //
	LOGIN_GENERATE_CREDENTIALS_FORM(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.NO), //
	LOGIN_CHANGE_PASS(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.NO), //
	LOGIN_CHANGE_PASS_FORM(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.NO), //
	SSL_NOTICE(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.NO), //
	//

	AJAX(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	EVENT(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	//

	FS_NAVIGATE(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_EDIT_FILE(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_SAVE_EDITED_FILE(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_SAVE_EDITED_FILE_AND_VIEW(AllowedFor.USER, Checks.NONE, Resets.LOCK_AFTER, StepBack.NO), //
	FS_CANCEL_EDITED_FILE(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_BEFORE, StepBack.NO), //
	FS_LOCK_OVERVIEW(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	FS_UNLOCK_FILE(AllowedFor.USER, Checks.NONE, Resets.LOCK_BEFORE, StepBack.NO), //
	FS_VIEW_OPTIONS(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_RENAME(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_VIEW_FILE(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.YES), //
	FS_SWITCH_VIEW_DETAILS(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //

	//

	FS_VIEW_FILE_AFTER_RESET_CLIENT_PW(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.YES), //
	FS_EDIT_FILE_AFTER_RESET_CLIENT_PW(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.YES), //
	IMAGE_VIEW_FULLSCREEN_AFTER_RESET_CLIENT_PW(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.YES), //
	IMAGE_VIEW_WITH_MENU_AFTER_RESET_CLIENT_PW(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.YES), //

	//

	FS_FILE_ENCRYPT_CLIENT_START(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_FILE_ENCRYPT_CLIENT_END(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_FILE_ENCRYPT_SERVER(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_FILE_DECRYPT_SERVER(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	//

	FS_DELETE_FILE(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_START_NOTE_EDIT(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_SAVE_NEW_NOTE(AllowedFor.USER, Checks.NONE, Resets.LOCK_AFTER, StepBack.NO), //
	FS_SAVE_NEW_NOTE_AND_VIEW(AllowedFor.USER, Checks.NONE, Resets.LOCK_AFTER, StepBack.NO), //
	FS_SAVE_NEW_NOTE_AND_START_NEXT(AllowedFor.USER, Checks.NONE, Resets.LOCK_AFTER, StepBack.NO), //
	FS_GOTO(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_BEFORE, StepBack.NO), //
	FS_COPY_FILE_SAME_DIR(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_COPY_FILE_CLIP(AllowedFor.USER, Checks.SELECTED_FILE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_MOVE_FILE_CLIP(AllowedFor.USER, Checks.SELECTED_FILE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_TO_CLIPBOARD(AllowedFor.USER, Checks.SELECTED_FILE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_NEW_FOLDER(AllowedFor.USER, Checks.SELECTED_FILE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_NEW_FILE(AllowedFor.USER, Checks.SELECTED_FILE, Resets.LOCK_AND_FILE_AFTER, StepBack.NO), //
	FS_FILE_LISTING(AllowedFor.USER, Checks.NONE, Resets.LOCK_AND_FILE_BEFORE, StepBack.NO), //
	FS_PUSH_TEXTVIEW(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	FS_TEXTVIEW_AFTER_OPTION_CHANGE(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FS_NUMBERS_TEXTVIEW(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	FS_SWITCH_TO_EDIT(AllowedFor.USER, Checks.NONE, Resets.LOCK_BEFORE, StepBack.NO), //
	//

	SYS_APP_INFO(AllowedFor.USER, Checks.NONE, Resets.LOCK_BEFORE, StepBack.NO), //
	SYS_SYSTEM_INFO(AllowedFor.USER, Checks.NONE, Resets.LOCK_BEFORE, StepBack.NO), //
	SYS_FJ_OPTIONS(AllowedFor.USER, Checks.NONE, Resets.LOCK_BEFORE, StepBack.NO), // AND FILE ??
	SYS_EXECUTE_JOB(AllowedFor.USER, Checks.NONE, Resets.LOCK_BEFORE, StepBack.NO), //
	//

	TXT_STATISTIC_MENU(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	TXT_STATISTIC_CALC(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	//

	PASSWORD_ASK_DECRYPT_SERVER(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	PASSWORD_ASK_ENCRYPT_SERVER_DIRECT_PASSWORD(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	PASSWORD_ASK_ENCRYPT_SERVER_HASHED_PASSWORD(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	PASSWORD_ASK_DECRYPT_CLIENT(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	PASSWORD_ASK_ENCRYPT_CLIENT(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	PASSWORD_CHECK_DECRYPT_SERVER(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	PASSWORD_CHECK_ENCRYPT_SERVER_DIRECT_PASSWORD(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	PASSWORD_CHECK_ENCRYPT_SERVER_HASHED_PASSWORD(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	PASSWORD_CHECK_DECRYPT_CLIENT(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	PASSWORD_CHECK_ENCRYPT_CLIENT(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	//

	BACKUPS_START(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	BACKUP_MAKE_FULLBACKUP(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	//

	IMAGE_VIEW_WITH_MENU(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	IMAGE_VIEW_FULLSCREEN(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	IMAGE_PREV(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	IMAGE_PREV_FULL(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	IMAGE_NEXT(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	IMAGE_NEXT_FULL(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	//

	FILE_UPLOAD_DEFAULT(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	FILE_UPLOAD(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	FILE_DOWNLOAD_DEFAULT(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	FILE_DOWNLOAD_ORIGINAL(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FILE_DOWNLOAD_DECRYPTED(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.NO), //
	FILE_DOWNLOAD_DECRYPTED_AFTER_RESET_CLIENT_PW(AllowedFor.USER, Checks.SELECTED_FILE, Resets.NONE, StepBack.YES), //
	//

	KVDB_EDIT_START(AllowedFor.ADMIN, Checks.NONE, Resets.NONE, StepBack.NO), //
	KVDB_EDIT(AllowedFor.ADMIN, Checks.NONE, Resets.NONE, StepBack.NO), //
	KVDB_DELETE(AllowedFor.ADMIN, Checks.NONE, Resets.NONE, StepBack.NO), //
	KVDB_INSERT(AllowedFor.ADMIN, Checks.NONE, Resets.NONE, StepBack.NO), //
	KVDB_RESET(AllowedFor.ADMIN, Checks.NONE, Resets.NONE, StepBack.NO), //
	NEW_USERS(AllowedFor.ADMIN, Checks.NONE, Resets.NONE, StepBack.NO), //
	UNLOCK_NEW_USER(AllowedFor.ADMIN, Checks.NONE, Resets.NONE, StepBack.NO), //
	//

	DEMO_PAGE(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.NO), //
	VIEW_LICENCE_ATTRIBUTION(AllowedFor.ANYBODY, Checks.NONE, Resets.NONE, StepBack.YES), //
	ENTER_KVDB_PASSWORD(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.YES), //
	SAVE_KVDB_PASSWORD(AllowedFor.USER, Checks.NONE, Resets.NONE, StepBack.YES), //
	//

	;

	private AllowedFor allowedFor;

	private Resets resets;

	private Checks checks;

	private StepBack stepBack;

	private Condition(AllowedFor allowed, Checks checks, Resets resets, StepBack stepBack) {
		this.allowedFor = allowed;
		this.checks = checks;
		this.resets = resets;
		this.stepBack = stepBack;
	}

	public enum AllowedFor {
		NOOONE, ANYBODY, USER, ADMIN
	}

	public enum Checks {
		NONE, SELECTED_FILE
	}

	public enum Resets {
		NONE, LOCK_BEFORE, LOCK_AND_FILE_BEFORE, LOCK_AFTER, LOCK_AND_FILE_AFTER;
	}

	public enum StepBack {
		YES, NO;
	}

	public AllowedFor getAllowedFor() {
		return allowedFor;
	}

	public Checks getChecks() {
		return checks;
	}

	public Resets getResets() {
		return resets;
	}

	public StepBack getStepBack() {
		return stepBack;
	}

}
