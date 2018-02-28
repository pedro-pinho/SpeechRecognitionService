package br.com.irisbot.asr;


public class MapSegmentation {
	private Integer id;
	private Long start;
	private Long length;
	
	
	public MapSegmentation() {
		//
	}
	public MapSegmentation(Integer id, Long start, Long length) {
		this.id = id;
		this.start = start;
		this.length = length;
	}
	public boolean findMap(Integer id) {
		if (this.id == id)
			return true;
		return false;
	}
	/**
	 * Getters and Setters
	 */
	public void setId(Integer id) {
		this.id = id;
	}
	public Integer getId() {
		return this.id;
	}
	
	public void setStart(Long start) {
		this.start = start;
	}
	public Long getStart() {
		return this.start;
	}
	
	public void setLength(Long length) {
		this.length = length;
	}
	public Long getLength() {
		return this.length;
	}
}
/*public class MapSegmentation {
	*//**
	 * Map with segment info. LIUM fruit.
	 * <String, <String,String>>
	 * <Id, 	<start, length>>
	 *//*
	private Map<Integer, Entry<Long, Long>> mapSegment = null;
    private static Map<Integer, Entry<Long, Long>> createMap()
    {
        Map<Integer,Entry<Long, Long>> myMap = new HashMap<Integer,Entry<Long, Long>>();
        Map.Entry<Long, Long> first = newEntry(new Long(0), new Long(0));
        myMap.put(0, first);
        return myMap;
    }
	public MapSegmentation() {
		this.mapSegment = createMap();
	}
	public void put(int id, long start, long length) {
		this.mapSegment.put(id, newEntry(start, length));
	}
	public Map<Integer, Entry<Long, Long>> getAll() {
		return this.mapSegment;
	}
	*//**
     * Creates a new Entry object given a key-value pair.
     * This is just a helper method for concisely creating a new Entry.
     * @param key   key of the entry
     * @param value value of the entry
     * 
     * @return  the Entry object containing the given key-value pair
     *//*
    private static <K,V> Map.Entry<K,V> newEntry(K key, V value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }

}
*/