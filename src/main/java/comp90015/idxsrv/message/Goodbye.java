package comp90015.idxsrv.message;

@JsonSerializable
public class Goodbye extends Message {
    
    @JsonElement
	public String msg;
	
	public Goodbye() {
		
	}
	
	public Goodbye(String msg) {
		this.msg=msg;
	}
}
