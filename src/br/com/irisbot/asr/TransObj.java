package br.com.irisbot.asr;


public class TransObj {
	private String 	transcription;
	private Double 	confidence;
	private int 	frame;
	private Boolean is_final;
	private long 	time;
	private int 	id;
	
	public TransObj(String transcription, Double confidence, int frame, Boolean is_final, long time) {
		if (transcription!="")
			this.transcription = transcription;
		if (confidence!=null)
			this.confidence    = confidence;
		if (frame!=-1)
			this.frame         = frame;
		if (is_final!=null)
			this.is_final      = is_final;
		if (time!=-1)
			this.time          = time;
		
	}

	public TransObj() {
		this.transcription = "";
		this.confidence    = null;
		this.frame         = -1;
		this.is_final      = null;
		this.time          = -1;
		this.id            = -1;
	}

	public boolean isEmpty() {
		if (this.transcription==""
				&& this.confidence==null
				&& this.frame==-1
				&& this.is_final==null
				&& this.time==-1
				&& this.id == -1)
			return true;
		return false;
	}
	/**
	 * Can we send the transcription to cli?
	 * Sync later
	 * @return bool
	 */
	public boolean isFull() {
		if (this.transcription!=""
				&& this.frame!=-1
				&& this.is_final!=null
				&& this.time!=-1)
			return true;
		return false;
	}
	/**
	 * If this transcription belongs to the frame on param, return true
	 * Otherwise false
	 * @param frame
	 * @return
	 */
	public boolean belongToFrame(int frame) {
		if (this.frame == frame) {
			return true;
		}
		return false;
	}
	
	/**
	 * Getters and setters of every field
	 */
		
	public void setTrans(String transcription) {
		this.transcription = transcription;
	}
	public String getTrans() {
		return this.transcription;
	}
	
	
	public void setConfidence(String confidence) {
		try {
			this.confidence = Double.parseDouble(confidence);
		} catch (NumberFormatException e) {
			this.confidence = new Double(0);
			e.printStackTrace();
		}
		
	}
	public void setConfidence(Double confidence) {
		this.confidence = confidence;
	}
	public Double getConfidence() {
		return this.confidence;
	}
	
	
	public void setFrame(int frame) {
		this.frame = frame;
	}
	public int getFrame() {
		return this.frame;
	}
	
	
	public void setIsFinal(boolean is_final) {
		this.is_final = is_final;
	}
	public boolean getIsFinal() {
		if (is_final==null)
			return false;
		return this.is_final;
	}
	
	
	public void setTime(long time) {
		this.time = time;
	}
	public long getTime() {
		return this.time;
	}
	
	
	public void setId(int id) {
		this.id = id;
	}
	public int getId() {
		return this.id;
	}

	
	public String getText() {
		String partialTrans = "";
		if (this.transcription.indexOf("transcript:")>-1) {
			partialTrans = this.transcription.substring(
					this.transcription.indexOf("transcript:")+12);
			//removing unecessary "
			partialTrans = partialTrans.replaceAll("\"", "");
			partialTrans = partialTrans.substring(0, partialTrans.indexOf("}"));
		}
		return partialTrans;
	}
	/*public static void main(String[] args) {}*/
}
