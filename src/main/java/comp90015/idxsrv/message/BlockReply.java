package comp90015.idxsrv.message;

@JsonSerializable
public class BlockReply extends Message {
    
    //the name of the file for which the block data is for
    @JsonElement
	public String filename;

    //the hash of the file for which the block data is for (provided by the `FileDescr` class)
    public String fileMd5;

    //the index of the provided block
    public Integer blockIdx;

    //a Base 64 Encoded string representing the byte array of the block
    public String bytes;
	
	public BlockReply() {
		
	}
	
	public BlockReply(String filename, String fileMd5, Integer blockIdx, String bytes ) {
		this.filename=filename;
        this.blockIdx=blockIdx;
        this.bytes=bytes;
        this.fileMd5=fileMd5;
	}
}
