package comp90015.idxsrv.message;

@JsonSerializable
public class BlockRequest extends Message {
    
    //the name of the file containing the requested block
    @JsonElement
	public String filename;
    //the MD5 hash of the file (provided by the `FileDescr` class)
    public String fileMd5;
    //the index of the block requested
    @JsonElement
    public int blockIdx;
	
	public BlockRequest() {
		
	}
	
	public BlockRequest(String filename, String fileMd5, Integer blockIdx) {
		this.filename=filename;
        this.fileMd5=fileMd5;
        this.blockIdx=blockIdx;
	}
}
