package st.cs.uni.saarland.de;

import soot.jimple.Stmt;

public class FlowdroidResults {
	private final FlowdroidEndpoint source;
	public FlowdroidEndpoint getSource() {
		return source;
	}

	private final FlowdroidEndpoint sink;
	
	public FlowdroidEndpoint getSink() {
		return sink;
	}
	
	private final String[] path;
	
	public String[] getPath(){
		return path;
	}
	
	public FlowdroidResults(FlowdroidEndpoint source, FlowdroidEndpoint sink, String[] path){
		this.source = source;
		this.sink = sink;
		this.path = path;
	}
	
	@Override
	public String toString(){
		return String.format("%s -> %s", source, sink);
	}
	
	@Override
	public int hashCode(){
		return source.hashCode() ^ sink.hashCode();
	}
	
	@Override
	public boolean equals(Object toCompare){
		if(!(toCompare instanceof FlowdroidResults)){
			return false;
		}		
		FlowdroidResults obj2 = (FlowdroidResults)toCompare;
		if(this.source == null || this.sink == null || obj2.source == null || obj2.sink == null){
			return false;
		}
		return this.source.equals(obj2.source) && this.sink.equals(obj2.sink);
	}
}
