package st.cs.uni.saarland.de;

import com.thoughtworks.xstream.XStream;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.MHGDominatorsFinder;
import soot.util.MultiMap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MudflowResultsHandler implements ResultsAvailableHandler {
	
	protected String resultsDir;
	protected String apkName;
	private List<String> uriMethodSignatures;
	private List<String> getOfContentResolvers;

	public MudflowResultsHandler(String resultsDir) {
		if (resultsDir == null || resultsDir.isEmpty()) {
			this.resultsDir = "results";
		} else {
			this.resultsDir = resultsDir;
		}
	}
	
	public void setApkName(String apkName){
		this.apkName = apkName;
	}
	
	protected static void createDirIfNotExsist(String name){
		File theDir = new File(name);
		if (!theDir.exists()) {
			theDir.mkdir();
		}
	}
	
	private boolean doesSignatureAccessContentResolver(String signature){
		if(getOfContentResolvers == null){
			getOfContentResolvers = new ArrayList<>();
			getOfContentResolvers.add(CONTENT_RESOLVER_CONSTANTS.GET_BLOB);
			getOfContentResolvers.add(CONTENT_RESOLVER_CONSTANTS.GET_COLUMN_NAME);
			getOfContentResolvers.add(CONTENT_RESOLVER_CONSTANTS.GET_COLUMN_NAMES);
			getOfContentResolvers.add(CONTENT_RESOLVER_CONSTANTS.GET_INT);
			getOfContentResolvers.add(CONTENT_RESOLVER_CONSTANTS.GET_LONG);
			getOfContentResolvers.add(CONTENT_RESOLVER_CONSTANTS.GET_STRING);
			getOfContentResolvers.add(CONTENT_RESOLVER_CONSTANTS.GET_TYPE);
		}
		return getOfContentResolvers.contains(signature);
	}
	
	private boolean doesSignatureHaveUri(String signature){
		if(uriMethodSignatures == null){
			uriMethodSignatures = new ArrayList<>();
		    uriMethodSignatures.add(CONTENT_RESOLVER_CONSTANTS.QUERY);
		    uriMethodSignatures.add(CONTENT_RESOLVER_CONSTANTS.INSERT);
		    uriMethodSignatures.add(CONTENT_RESOLVER_CONSTANTS.BULKINSERT);
		    uriMethodSignatures.add(CONTENT_RESOLVER_CONSTANTS.UPDATE);
		    uriMethodSignatures.add(CONTENT_RESOLVER_CONSTANTS.DELETE);
		}
	    return uriMethodSignatures.contains(signature);
	}
	
	private String resolveQueryStmtAndUri(final Body b, final Unit u, SootMethod method){
		if(!(u instanceof AssignStmt)){
			return method.getSignature();
		}
		AssignStmt stmt = (AssignStmt)u;
		Value queryReq = stmt.getRightOp().getUseBoxes().get(stmt.getRightOp().getUseBoxes().size() - 1).getValue();
		MHGDominatorsFinder<Unit> dominatorsFinder = new MHGDominatorsFinder<Unit>(new ExceptionalUnitGraph(b));
		Unit currentUnit = u;
		while (dominatorsFinder.getImmediateDominator(currentUnit) != null){
			currentUnit = dominatorsFinder.getImmediateDominator(currentUnit);
			if(currentUnit instanceof AssignStmt){
				AssignStmt asStmt = (AssignStmt)currentUnit;
				if(asStmt.getLeftOp().equals(queryReq) && asStmt.containsInvokeExpr() && doesSignatureHaveUri(asStmt.getInvokeExpr().getMethod().getSignature())){
					return resolveUri(b, asStmt, asStmt.getInvokeExpr().getMethod(), asStmt.getInvokeExpr().getArgs());
				}
			}
		}
		return method.getSignature();
	}
	
	private String resolveUri(final Body b, final Unit u, SootMethod method, List<Value> parameterValues){
		final Value register = parameterValues.get(0);

        if (!(register instanceof Local) && !(register.getType().equals("android.net.Uri")))
            return null;

        Local localRegister = (Local) register;

        MHGDominatorsFinder<Unit> dominatorsFinder = new MHGDominatorsFinder<Unit>(new ExceptionalUnitGraph(b));
        UriFinderSwitch uriSwitch = new UriFinderSwitch(localRegister, b.getMethod());

        Unit currentUnit = u;
        while (dominatorsFinder.getImmediateDominator(currentUnit) != null && !uriSwitch.isStop()) {
            currentUnit = dominatorsFinder.getImmediateDominator(currentUnit);
            currentUnit.apply(uriSwitch);
        }
        String uri = uriSwitch.getUri();
        if(uri != null){
            return method.getSignature().replace("(android.net.Uri,", String.format("(%s,", uri.replace("<","").replace(">","")));
        }
        return method.getSignature();
	}

	@Override
	public void onResultsAvailable(IInfoflowCFG cfg, InfoflowResults results) {
		List<Stmt> sourcesInDataflows = new ArrayList<>();
		List<Stmt> sinksInDataflows = new ArrayList<>();
		List<FlowdroidResults> flowdroidResults = new ArrayList<>();
		MultiMap<ResultSinkInfo, ResultSourceInfo> res = results.getResults();
		for(ResultSinkInfo sinkInfo : res.keySet()){
			//we are interested only in invoke expressions
			if(!sinkInfo.getSink().containsInvokeExpr()){
				continue;
			}
			SootMethod sink = sinkInfo.getSink().getInvokeExpr().getMethod();
			SootMethod callerOfSink = cfg.getMethodOf(sinkInfo.getSink());
			final String sinkSignature = sink.getSignature();
			
			FlowdroidEndpoint sinkEndpoint;
			
			if(START_ACTIVITY_CONSTANTS.getStartActivityMethods().contains(sink.getSubSignature())){
				//we should collect more data about the startActivity
				AnalyzeIntents intentAnalyzer = new AnalyzeIntents(sink, sinkInfo.getSink(), callerOfSink);
				intentAnalyzer.run();
				sinkEndpoint = intentAnalyzer.getIntentInfo();
			}
			else{
				sinkEndpoint = new FlowdroidEndpoint(sinkSignature, callerOfSink.getSignature());
			}
			
			for(ResultSourceInfo sourceInfo : results.getResults().get(sinkInfo)){
				//we are interested only in invoke expressions
				if(!sourceInfo.getSource().containsInvokeExpr()){
					continue;
				}
				SootMethod source = sourceInfo.getSource().getInvokeExpr().getMethod();
				SootMethod callerOfSource = cfg.getMethodOf(sourceInfo.getSource());
				String sourceSignature = source.getSignature();
				if(doesSignatureAccessContentResolver(sourceSignature)){
					sourceSignature = resolveQueryStmtAndUri(callerOfSource.getActiveBody(), sourceInfo.getSource(), source);
				}
				else if(doesSignatureHaveUri(sourceSignature)){
					sourceSignature = resolveUri(callerOfSource.getActiveBody(), sourceInfo.getSource(), source, sourceInfo.getSource().getInvokeExpr().getArgs());
				}
				sourcesInDataflows.add(sourceInfo.getSource());
				sinksInDataflows.add(sinkInfo.getSink());
				FlowdroidEndpoint sourceEndpoint = new FlowdroidEndpoint(sourceSignature, callerOfSource.getSignature());
				List<DataflowPathItem> path = new ArrayList<>();
				if(sourceInfo.getPath() != null){
					for(Stmt pathEntry: sourceInfo.getPath()){						
						DataflowPathItem pItem = new DataflowPathItem(cfg.getMethodOf(pathEntry), pathEntry, getNumberInMethodUnits(pathEntry, cfg.getMethodOf(pathEntry).getActiveBody()));
						path.add(pItem);
					}
				}
				
				FlowdroidResults flowDroidResults = new FlowdroidResults(sourceEndpoint, sinkEndpoint, path);
				flowdroidResults.add(flowDroidResults);
				System.out.println(flowDroidResults);
			}
		}
		
		//Add NO_SENSITIVE_SINK
		FlowdroidEndpoint nsSinkEndpoint = new FlowdroidEndpoint("NO_SENSITIVE_SINK", "NO_SENSITIVE_SINK");
		for(Stmt source : MudflowHelper.getCollectedSources()){
			if(!sourcesInDataflows.contains(source) && source.containsInvokeExpr() && !source.getInvokeExpr().getMethod().getSignature().equals(CONTENT_RESOLVER_CONSTANTS.QUERY)){
				FlowdroidEndpoint sourceEndpoint = new FlowdroidEndpoint(source.getInvokeExpr().getMethod().getSignature(), cfg.getMethodOf(source).getSignature());
				FlowdroidResults flowDroidResults = new FlowdroidResults(sourceEndpoint, nsSinkEndpoint, new ArrayList<>());
				flowdroidResults.add(flowDroidResults);
				System.out.println(flowDroidResults);
			}
		}
		
		//Add NO_SENSITIVE_SOURCE
		FlowdroidEndpoint nsSourceEndpoint = new FlowdroidEndpoint("NO_SENSITIVE_SOURCE", "NO_SENSITIVE_SOURCE");
		for(Stmt sink : MudflowHelper.getCollectedSinks()){
			if(!sinksInDataflows.contains(sink) && sink.containsInvokeExpr()){
				FlowdroidEndpoint sinkEndpoint = new FlowdroidEndpoint(sink.getInvokeExpr().getMethod().getSignature(), cfg.getMethodOf(sink).getSignature());					
				FlowdroidResults flowDroidResults = new FlowdroidResults(nsSourceEndpoint, sinkEndpoint, new ArrayList<>());
				flowdroidResults.add(flowDroidResults);
				System.out.println(flowDroidResults);
			}
		}

		saveDataflows(flowdroidResults);
	}
	
	private int getNumberInMethodUnits(Stmt stmt, Body b){
		int counter = 0;
		for(final Unit u: b.getUnits()){
			if(u.equals(stmt)){
				return counter;
			}
			counter++;
		}
		return -1;
	}

	protected void saveDataflows(List<FlowdroidResults> flowdroidResults) {
		XStream xStream = new XStream();
		xStream.processAnnotations(FlowdroidResults.class);
		xStream.processAnnotations(FlowdroidEndpoint.class);
		xStream.setMode(XStream.NO_REFERENCES);
		createDirIfNotExsist(resultsDir);
		try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(String.format(resultsDir + File.separator + "%s_results.xml", this.apkName)), StandardCharsets.UTF_8))) {
            bw.append(xStream.toXML(flowdroidResults));
        } catch (IOException exception) {
            exception.printStackTrace();
        }
		System.out.println(String.format("Found %d results", flowdroidResults.size()));
	}

}
