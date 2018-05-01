package mfi.files.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface FilesJob {

	public String cron();

	public int failureCountUntilStartLogging();

	public boolean hasCryptoConfig();
}
