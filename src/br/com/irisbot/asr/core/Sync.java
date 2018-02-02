package br.com.irisbot.asr.core;

import java.util.ArrayList;
import java.util.List;
import br.com.irisbot.asr.MapSegmentation;
import br.com.irisbot.asr.TransObj;

public class Sync {
	/**
	 * Trace of order of dialog
	 * E.g.:Agent,Client,Client,Agent,Client
	 */
	public static List<Integer> dialogTrace = new ArrayList<Integer>();
	private List<TransObj> list;
	private List<Integer> possibleIds = new ArrayList<Integer>();
	
	
	public Sync(List<TransObj> list) {
		this.list = list;
		/**
		 * "There was one time my brother transformed himself into a snake
		 * because he knows how much I like snakes,
		 * and so I picked the snake up to admire it, but
		 * then he turned back and went 'AAHH! It's me!' And then he stabbed me."
		 */
	}
	
	public void checkAndAddPossibilities(Integer id) {
		if (!this.possibleIds.contains(id) && this.possibleIds.size()<2) {
			this.possibleIds.add(id);
		}
	}
	public List<Integer> getPossibilities() {
		return this.possibleIds;
	}
	public void setBallOfSpeaking(int e) {
		dialogTrace.add(e);
	}
	public int getLastBallOfSpeaking() {
		return dialogTrace.get(dialogTrace.size()-1);
	}
	
	//TODO review when assume things
	class Chunks {
		//Goal: Sync in real time, as more info comes, and optimized
		//Since we call this every frame (from diarization algorithm), it will execute this a lot of times
		public Chunks(int frame, long startOfFrame, List<MapSegmentation> ms) {
			//PS: one map per frame
			//Step 1: Search inside transcriptions which one is in this same frame
			try {
				for (TransObj transObj : list) //transcriptions streamed by google so far
				{
					if (!transObj.belongToFrame(frame)) {continue;}
					MapSegmentation bestSeg = null;
					/*
					 * Step 2: Foreach of these, compare transcription time with 
					 * (start time frame + start time map segment)
					 * for every piece of mapSegment
					 * If doens't find an exact match, find closest (norm 1 distance)
					 */
					for (MapSegmentation mapSegmentation : ms)
					{
						long start = mapSegmentation.getStart();
						//long distance = Math.abs(transObj.getTime() - Math.abs(start+startOfFrame));
						
						if (transObj.getTime() == (start+startOfFrame))
						{
							bestSeg = mapSegmentation;
						}
					}
					if (bestSeg==null) {
						bestSeg = findNearNeighbour(ms, transObj,startOfFrame);
					}
					infereLocutor(bestSeg, transObj);
						
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			
		}
		/**
		 * We didn't got exact match, so find nearest neighbour
		 * @param ms
		 * @param transObj
		 * @param startOfFrame
		 * @return
		 */
		public MapSegmentation findNearNeighbour(List<MapSegmentation> ms, TransObj transObj, long startOfFrame) {
			MapSegmentation bestSeg = null;
			for (MapSegmentation mapSegmentation : ms)
			{
				long start = mapSegmentation.getStart();
				long closest = 10000;
				long distance = Math.abs(transObj.getTime() - Math.abs(start+startOfFrame));
				
				if (distance < closest) {
					closest = distance;
					bestSeg = mapSegmentation;
				}
			}
			return bestSeg;
		}
		
		public void infereLocutor(MapSegmentation ms, TransObj transObj) {
			Probability probControl = new Probability();
			List<Integer> maybe = getPossibilities();
			
			/**
			 * Step 3: Infere which one of the locutors is this
			 */						
			if (dialogTrace.isEmpty())
			{
				//If its the first first, more likely to be agent
				probControl.increase("agent", new Double(0.3));
			} else
			{
				//If the last guy is not equal do this id, it changed middle frame
				//if (getLastBallOfSpeaking()!=ms.getId())
				//Here we also assume that the first id identified was from agent
				if (ms.getId()==maybe.get(0))
				{
					probControl.increase("agent", new Double(0.2));
				}
				if (ms.getId()==maybe.get(1))
				{
					probControl.increase("client", new Double(0.5));
				}
				/**
				 * Step 4: Since it changed
				 * Cut transcript at that time, make 2 transcriptions 
				 */
				//cutTranscription(ms);

			}
			if (probControl.getWinner(transObj.getId())=="agent") {
				transObj.setId(maybe.get(0));
			} else {
				transObj.setId(maybe.get(1));
			}
			System.out.println(transObj.getId() + " " +transObj.getText());
		}
		
		public void cutTranscription(MapSegmentation ms) {
			/**
			 * Step 1: Find out percentage of frame that was cropped
			 * Step 2: Use this percentage to cut transcription
			 * 	Step 2.5: Dont cut words
			 */
			//long difference = (ms.getStart()-ms.getLength());
			
		}
		
		public void addTranscription(TransObj to) {
			list.add(to);
		}
		
		/*public void lastOneIsTheGuy(Probability probControl) {
			int who = getLastBallOfSpeaking();
			//List<Integer> possibilities
			if (who==0) {
				probControl.increase("agent", new Double(0.2));
			} else if (who==3) {
				probControl.increase("client", new Double(0.2));
			}
		}*/
	}
	/**
	 * Simulate probability
	 * Always 0<=x<=1
	 * p(agent) = p(client) - 1;
	 * p(agent)+p(client) = 1
	 * @author pedro
	 */
	class Probability {
		private double pAgent = new Double(0);
		private double pClient = new Double(0);
		private Integer idForAgent = null;
		private Integer idForClient = null;
		
		public Probability() {
			this.pAgent = new Double(0.5);
			this.pClient = new Double(0.5);
		}
		
		public Probability(double a, double c) {
			this.pAgent = a;
			this.pClient = c;
		}
		public void increase(String which, double more) {
			if (which=="agent") {
				this.pAgent = this.pAgent + more;
				if (this.pAgent > 1) this.pAgent = 1;
				this.pClient = this.pAgent - 1;
			} else if (which=="client") {
				this.pClient = this.pClient + more;
				if (this.pClient > 1) this.pClient = 1;
				this.pAgent = this.pClient - 1;
			} else {
				throw new java.lang.Error("Try again...");
			}
		}
		public String getWinner(Integer id) {
			//map the id with cli or agent to always att that on next iterations
			if (id==idForAgent)
				return "agent";
			if (id==idForClient)
				return "client";
			if (pAgent>pClient)				
				return "agent";
			return "client";
		}
	}
	
}
