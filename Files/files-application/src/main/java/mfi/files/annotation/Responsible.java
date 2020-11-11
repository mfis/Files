package mfi.files.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import mfi.files.model.Condition;

@Retention(RetentionPolicy.RUNTIME)
public @interface Responsible {

	public Condition[] conditions();
}
