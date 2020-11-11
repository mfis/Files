package mfi.files.model;

import java.lang.reflect.Method;

public class ResponsibleMethod {

	public ResponsibleMethod(Object clazz, Method method) {
		this.setClazz(clazz);
		this.setMethod(method);
	}

	private Object clazz;

	private Method method;

	public Object getClazz() {
		return clazz;
	}

	public void setClazz(Object clazz) {
		this.clazz = clazz;
	}

	public Method getMethod() {
		return method;
	}

	public void setMethod(Method method) {
		this.method = method;
	}

}
