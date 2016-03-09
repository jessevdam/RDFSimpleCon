package nl.wur.ssb.RDFSimpleCon;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.vfs2.FileObject;
import org.apache.jena.atlas.web.auth.SimpleAuthenticator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.update.UpdateAction;



public class RDFSimpleCon
{
	private Model localDb;
	private String server;
	private int port;
	private String finalLocation;
	private String graph;
	private SimpleAuthenticator authen;
	private boolean eachThreadSeperate = false;
	private int counter = 0;
	private HashMap<Long,Integer> threadMap = new HashMap<Long,Integer>();
	private int maxThreadCount = 1;
	
	public RDFSimpleCon(String config, String tmpDir) throws Exception
	{
		try
		{
	    if(config.indexOf("[") != -1)
	    {
	  	  Matcher temp = Pattern.compile("(.*)\\[(.*)\\]").matcher(config);
	  	  if(temp.matches() == false)
	  	  	throw new Exception("invalid config string: " + config);
	  	  config = temp.group(1);
	  	  graph = temp.group(2);
	    }
	    if(config.isEmpty())
	    {
	    	Dataset dataset = createEmptyStore(tmpDir);
			  if(graph == null)
				  localDb = dataset.getDefaultModel();
			  else
				  localDb = dataset.getNamedModel(graph);
	    }
	    else if(config.startsWith("file://"))
			{
	    	String fileName = config.substring("file://".length());
	    	RDFFormat fileFormat = null;
		    if(config.indexOf("{") != -1)
		    {
		  	  Matcher temp = Pattern.compile("(.*)\\{(.*)\\}").matcher(fileName);
		  	  if(temp.matches() == false)
		  	  	throw new Exception("invalid config string: " + config);
		  	  fileName = temp.group(1);
		  	  fileFormat = RDFFormat.getFormat(temp.group(2));
		    }
				File file = new File(fileName);
			  Dataset dataset = null;
				if(file.isDirectory())
				{
				  dataset = TDBFactory.createDataset(file.toString());
				}
				else
				{
					dataset = createEmptyStore(tmpDir);
					RDFDataMgr.read(dataset,file.toString(),fileFormat != null ? fileFormat.getLang() : null);
				}
			  if(graph == null)
				  localDb = dataset.getDefaultModel();
			  else
				  localDb = dataset.getNamedModel(graph);
			}
			else
			{
			  String username = null;
			  String pass = null;
		    if(config.indexOf("@") != -1)
		    {
		    	String temp[] = config.split("@");
		    	String temp2[] = temp[0].split(":");
		  	  if(temp2.length != 2)
		  		  throw new Exception("invalid config string: " + config);
		  	  username = temp2[0];
		  	  pass = temp2[1];
		  	  config = temp[1];
		    }

				Matcher matcher = Pattern.compile("http://(.+):([\\d]+)/(.*)").matcher(config);
				if(!matcher.matches())
				{
					matcher = Pattern.compile("http://(.+)/(.*)").matcher(config);
					matcher.matches();
					this.server = matcher.group(1);
					this.port = 80;
					this.finalLocation = matcher.group(2);
				}
				else
				{
					this.server = matcher.group(1);
					this.port = Integer.parseInt(matcher.group(2));
					this.finalLocation = matcher.group(3);
				}
		    if(username != null)
		    	this.setAuthen(username,pass);
		    //only used for prefixes
		    Dataset dataset = createEmptyStore(tmpDir);
		    localDb = dataset.getDefaultModel();
			}
		}
		catch(Throwable th)
		{
			throw new Exception("invalid config string: " + config,th);
		}	  
		this.setNsPrefix("rdf","http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		this.setNsPrefix("rdfs","http://www.w3.org/2000/01/rdf-schema#");
		this.setNsPrefix("owl","http://www.w3.org/2002/07/owl#");	
	}
	
	private Dataset createEmptyStore(String tmpDir)
	{
  	if(tmpDir == null)
  		return TDBFactory.createDataset();
  	else
  		return TDBFactory.createDataset(tmpDir);
	}
	public RDFSimpleCon(String config) throws Exception
	{
		this(config,null);
	}
	
	public RDFSimpleCon(FileObject file,RDFFormat format) throws IOException
	{
		Dataset dataset = createEmptyStore(null);
		RDFDataMgr.read(dataset,file.getContent().getInputStream(),format.getLang());		
	}
	
	/*public RDFConnection(String dir,String graph,boolean local)
	{
		Dataset dataset = TDBFactory.createDataset(dir);
		localDb = dataset.getNamedModel(graph);
	}
	
	public RDFConnection(String server,String graph)
	{
    this.setServerGraph(server,graph);
	}*/
	
	public void enableEachThreadSeperatePort(int threadCount)
	{
		this.eachThreadSeperate = true;
		this.maxThreadCount = threadCount;
	}
	
  public void setAuthen(String user,String pass)
  {
  	authen = new SimpleAuthenticator(user,pass.toCharArray());
  }
	
	private QueryExecution createQueryFromFile(String queryFile,Object ... args)  throws Exception
	{
    Object toPass[] = args;
    
		String header = Util.readFile("queries/header.txt");
		String content = Util.readFile(queryFile);
		String queryString = header + content;
		Pattern path = Pattern.compile("^((FRoM)|(WITH)|(USING))\\s+<\\%\\d+\\$S>.*$",Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    if(path.matcher(queryString).find())
    {
   	  toPass = new Object[args.length + 1];
      System.arraycopy(args,0,toPass,1,args.length);
      toPass[0] = this.graph;
      args = toPass;
    	if(this.graph == null)
    	{
    		queryString = path.matcher(queryString).replaceAll("");
    	}
    }
    queryString = String.format(queryString,args);	
    //System.out.println(queryString);
	  return createQuery(queryString);
	}
	
	private QueryExecution createQuery(String queryString)  throws Exception
	{
		Query query = QueryFactory.create(queryString);
		if(this.server != null)
		{
	    int port = this.port;
	    if(this.eachThreadSeperate)
	    {
	  	  port = this.getThreadPortNum();
	    }
	    String server = "http://" + this.server + ":" + port + "/" + this.finalLocation;
  		QueryExecution qe = QueryExecutionFactory.sparqlService(server,query,authen);
	  	qe.setTimeout(7,TimeUnit.DAYS);
		  return qe;
		}
		else
		{
		  return QueryExecutionFactory.create(query,this.localDb,null);
		}
	}
	
	private int getThreadPortNum() throws Exception
	{
		long threadId = Thread.currentThread().getId();
		if(this.threadMap.containsKey(threadId))
			return this.threadMap.get(threadId) + this.port;
		int newCount = this.counter++;
		if(newCount > this.maxThreadCount)
			throw new Exception("max thread count reached");
		this.threadMap.put(threadId,newCount);
		return newCount + this.port;
	}

	public LinkedList<ResultLine> runQueryToMap(String queryFile, Object... args) throws Exception
	{
		queryFile = "queries/" + queryFile;
		QueryExecution qe = createQueryFromFile(queryFile,args);
		long millis = System.currentTimeMillis();
		ResultSet result = qe.execSelect();
		Iterable<HashMap<String, RDFNode>> walker = new Iteration<HashMap<String, RDFNode>>(new ResultIteratorRaw(result));
		LinkedList<ResultLine> res = new LinkedList<ResultLine>();
		for (HashMap<String, RDFNode> item : walker)
		{
			res.add(new ResultLine(item));
		}
		qe.close();
		System.out.println("time: " + (System.currentTimeMillis() - millis) + " for query " + queryFile);
		return res;		
	}

	public Iterable<ResultLine> runQuery(String queryFile,boolean preload,Object ... args) throws Exception
	{
	  queryFile = "queries/" + queryFile;
		QueryExecution qe = createQueryFromFile(queryFile,args);
		long millis = System.currentTimeMillis();
		ResultSet result = qe.execSelect();
		ResultIteratorRaw walker = new ResultIteratorRaw(result);// new Iteration<HashMap<String,RDFNode>>
		if(preload == false)
		{
			return new Iteration<ResultLine>(new ResultIterator(new Iteration<HashMap<String,RDFNode>>(walker),qe));
		}
		else
		{
			LinkedList<HashMap<String,RDFNode>> res = new LinkedList<HashMap<String,RDFNode>>();
			for(HashMap<String,RDFNode> item : new Iteration<HashMap<String,RDFNode>>(walker))
			{
				res.add(item);
			}
			qe.close();
			System.out.println("time: " + (System.currentTimeMillis() - millis) + " for query " + queryFile); 
			return new Iteration<ResultLine>(new ResultIterator(new Iteration<HashMap<String,RDFNode>>(res.iterator()),null));
		}
	}
	
	public ResultSet runQueryDirect(String query) throws Exception
	{
		QueryExecution qe = createQuery(query);
		return qe.execSelect();
	}
			
	public String expand(String in)
	{
		String toRet = localDb.expandPrefix(in);
		if(!toRet.startsWith("http"))
			throw new RuntimeException("prefix not expanded: " + in);
		return toRet;
	}
	
	public void add(String subj,String pred,String obj)
	{
		synchronized(this)
		{
		  this.localDb.add(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createResource(expand(obj)));
		}
	}
	public void addLit(String subj,String pred,String obj)
	{
		synchronized(this)
		{
  		this.localDb.add(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createLiteral(obj));
		}
	}
	public void add(String subj,String pred,int val)
	{
		synchronized(this)
		{
		  this.localDb.add(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createTypedLiteral(val));
	  }
	}
	public void add(String subj,String pred,boolean val)
	{
		synchronized(this)
		{
		  this.localDb.add(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createTypedLiteral(val));
	  }	
	}
	public void add(String subj,String pred,float val)
	{
		synchronized(this)
		{
		  this.localDb.add(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createTypedLiteral(val));
 	  }
	}
	public void add(String subj,String pred,double val)
	{
		synchronized(this)
		{
		  this.localDb.add(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createTypedLiteral(val));
  	}	
	}
	
	public boolean contains(String subj,String pred)
	{
		synchronized(this)
		{
		  return this.localDb.contains(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)));
	  }
	}
	public boolean containsLit(String subj,String pred,String lit)
	{
		synchronized(this)
		{
		  return this.localDb.contains(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createLiteral(lit));
	  }
	}
	public boolean containsLit(String subj,String pred,int lit)
	{
		synchronized(this)
		{
		  return this.localDb.contains(this.localDb.createResource(expand(subj)),this.localDb.createProperty(expand(pred)),this.localDb.createTypedLiteral(lit));
		}
	}
	
	/*public boolean bgp(String subj,String pred,String obj)
	{
		
	}*/
	
	public PrefixMapping setNsPrefix(String prefix,String iri)
	{
		return this.localDb.setNsPrefix(prefix,iri);
	}
	
	public void close()
	{
		this.localDb.close();
	}
	
	public Model getModel()
	{
		return this.localDb;
	}
	 
	public void save(String file) throws IOException
	{
	  this.save(file,RDFFormat.TURTLE);
	}
	
	public void save(String file,RDFFormat format) throws IOException
	{
		//"RDF/XML", "RDF/XML-ABBREV", "N-TRIPLE", "TURTLE", (and "TTL") and "N3
		OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
		this.localDb.write(out,format.toString());
		out.close();
	}
	
  public void runUpdateQuery(String file,Object ...args)
  {
		try
		{
			String header = Util.readFile("queries/header.txt");
			String content = Util.readFile("queries/" + file);
			String query = header + content;
			query = String.format(query,args);		
			UpdateAction.parseExecute(query, this.localDb);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}  	
  }
	
	public void addAll(RDFSimpleCon other)
	{		
		this.localDb.add(other.localDb.listStatements());
	}
	
}












