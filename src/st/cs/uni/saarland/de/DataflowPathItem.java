package st.cs.uni.saarland.de;

import soot.SootMethod;
import soot.Unit;

public class DataflowPathItem {
	private final String callerMethod;
	
	public String getCallerMethod() {
		return callerMethod;
	}

	public String getUnit() {
		return unit;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	private final String unit;
	
	private final int lineNumber;
	
	public DataflowPathItem(SootMethod callerMethod, Unit u, int lineNumberInMethod){
		this.callerMethod = callerMethod.getSignature();
		this.unit = u.toString();
		this.lineNumber = lineNumberInMethod;
	}
}
