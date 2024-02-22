package jeongsik;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public class Server {
	public static void main(String[] args) { 
		final int PORT = 9000;
		
		// 서버 전체에서 모든 클라이언트의 소켓을 관리하는 데 사용
		Hashtable<String, Socket> clientHt = new Hashtable<>(); // 접속자를 관리하는 테이블

		try {
			ServerSocket serverSocket = new ServerSocket(PORT);
			String mainThreadName = Thread.currentThread().getName();
			/* main thread는 계속 accept()처리를 담당한다 */
			while (true) {
				System.out.printf("[서버-%s] Client접속을 기다립니다...\n", mainThreadName);
				Socket socket = serverSocket.accept();

				/* worker thread는 Client와의 통신을 담당한다. */
				WorkerThread wt = new WorkerThread(socket, clientHt);
				wt.start();
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
}
class WorkerThread extends Thread {
	private Socket socket;
	
	// 각 WorkerThread가 자신이 처리하는 클라이언트의 소켓을 관리하는데 사용
	private Hashtable<String, Socket> ht;

	// WorkerThread 객체를 초기화할때 사용하는 생성자
	public WorkerThread(Socket socket, Hashtable<String, Socket> ht) {
		this.socket = socket;
		this.ht = ht;
	}

	@Override
	public void run() {
		try {
			InetAddress inetAddr = socket.getInetAddress();
			System.out.printf("<서버-%s>%s로부터 접속했습니다.\n", getName(), inetAddr.getHostAddress());
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
//			BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			while(true) {
				/*client가 json오브젝트를 string으로 변환해서 보낸 것을 수신*/
				String line = br.readLine();
				//System.out.println("raw data=" + line);
				if(line == null)
					break;
				/*json패킷을 해석해서 알맞은 처리를 한다.
				 * 문자열 -> JSONObject 변환 -> cmd를 해석해서 어떤 명령인지?
				 * */
				
				// 읽어온 문자열 데이터를 JSON 객체로 변환
				JSONObject packetObj = new JSONObject(line);
				
				// 명령(cmd)당 알맞은 처리를 해줌
				// 변환된 JSON 객체를 처리하는 processPacket 메서드를 호출합, 이 메서드는 클라이언트의 요청에 따라 적절한 작업을 수행
				processPacket(packetObj);
				
			}
		}catch (Exception e) {
			System.out.printf("<서버-%s>%s\n", getName(), e.getMessage());
		}
	}
	
	private void processPacket(JSONObject packetObj) throws IOException {
		
		// 클라이언트에 응답을 하기 위한 json 오브젝트
		JSONObject ackObj = new JSONObject();
		
		// 어떤 종류의 패킷을 보냈는지 분류하기 위한 정보
		// Client에서 정한 키가 cmd이고 옆에 값들이 가각 "ID", ARITH"등등이 있고 그 값들을 불러 오는 값이 "cmd"이다.
		// JSON 객체 'packetObj'에서 가져온 "cmd" 필드의 값이 저장
		String cmd = packetObj.getString("cmd");
		
		// id 등록 요청
		if(cmd.equals("ID")) {
			// 클라이언트 요청 처리
			String id = packetObj.getString("id");
			ht.put(id, this.socket);	// 해시테이블에 id와 socket을 등록		
			
			System.out.printf("<서버-%s> Id=%s 등록\n", getName(), id);
			// 응답			
			ackObj.put("cmd", "ID");
			ackObj.put("ack", "ok");
			// Json Obj -> 문자열
			//String ack = new String(ackObj.toString().getBytes(), "utf-8");
			String ack = ackObj.toString();
			// 클라이언트한테 전송
			OutputStream out = this.socket.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
			pw.println(ack);
			pw.flush();
		}
		// 사칙연산 업무 결과 요청
		else if(cmd.equals("ARITH")) {
			// 요청 처리
			String id = packetObj.getString("id");
			String op = packetObj.getString("op");
			String val1 = packetObj.getString("val1");
			String val2 = packetObj.getString("val2");
			double v1 = Double.parseDouble(val1);
			double v2 = Double.parseDouble(val2);
			double result = arith(op, v1, v2);
			
			System.out.printf("<서버-%s> Id=%s 사칙연산 %s %s %s %f\n", getName(), id, val1, op, val2, result);
			
			// 응답
			ackObj.put("cmd", "ARITH");
			ackObj.put("ack", Double.toString(result));
			// Json Obj -> 문자열
			String ack = ackObj.toString();
			// 클라이언트한테 전송
			OutputStream out = this.socket.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
			pw.println(ack);
			pw.flush();
		}
		// 접속자 전체한테 채팅 메시지 전송
		else if(cmd.equals("ALLCHAT")) {
			String id = packetObj.getString("id");
			String msg = packetObj.getString("msg");
			
			System.out.printf("<서버-%s> Id=%s 전체 채팅 %s\n", getName(), id, msg);
			
			/* 클라이언트 응답 패킷 */
			// 응답
			ackObj.put("cmd", "ALLCHAT");
			ackObj.put("ack", "ok");
			// Json Obj -> 문자열
			String ack = ackObj.toString();
			// 클라이언트한테 전송
			OutputStream out = this.socket.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
			pw.println(ack);
			pw.flush();
			
			// 특정 yourid 사용 클라이언트에 전송 패킷
			JSONObject broadObj = new JSONObject();
			broadObj.put("cmd", "BROADCHAT");
			broadObj.put("id", id);
			broadObj.put("msg", msg);
			String strBroad = broadObj.toString();
			// 전체 전송
			broadcast(strBroad);
		}
		// 특정 id 대상한테 1:1 채팅
		else if(cmd.equals("ONECHAT")) {
			String id = packetObj.getString("id");
			String yourid = packetObj.getString("yourid");
			String msg = packetObj.getString("msg");
			
			System.out.printf("<서버-%s> 1:1 채팅 %s->%s : %s\n", getName(), id, yourid, msg);
			
			/* 클라이언트 응답 패킷 */
			// 응답
			ackObj.put("cmd", "ONECHAT");
			ackObj.put("ack", "ok");
			// Json Obj -> 문자열
			String ack = ackObj.toString();
			// 클라이언트한테 전송
			OutputStream out = this.socket.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
			pw.println(ack);
			pw.flush();
			
			// 전송 패킷 구성
			JSONObject uniObj = new JSONObject();
			uniObj.put("cmd", "UNICHAT");
			uniObj.put("id", id);
			uniObj.put("msg", msg);
			String strUni = uniObj.toString();
			// yourid 클라이언트한테 전송
			unicast(strUni, yourid);
		}else if(cmd.equals("REQIDLIST")) {
			String id = packetObj.getString("id");
			
			System.out.printf("<서버-%s> Id=%s 접속 ID 요청 \n", getName(), id);
			
			/* 클라이언트 응답 패킷 */
			// 응답
			ackObj.put("cmd", "REQIDLIST");
			
			// id를 JSON의 배열로 넣기 위한 선언
			JSONArray idList = new JSONArray();
			// id를 JSON배열에 넣기
			Set<String> idSet = ht.keySet();
			Iterator<String> idIter = idSet.iterator();
			while(idIter.hasNext()) {
				String _id = idIter.next();
				idList.put(_id);
			}
			ackObj.put("list", idList);
			// Json Obj -> 문자열
			String ack = ackObj.toString();
			// 클라이언트한테 전송
			OutputStream out = this.socket.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
			pw.println(ack);
			pw.flush();
		}else if(cmd.equals("QUIT")) {
			String id = packetObj.getString("id");
			
			System.out.printf("<서버-%s> Id=%s QUIT 요청 \n", getName(), id);
			
			// 관리에서 제외
			ht.remove(id);
			
			ackObj.put("cmd", "QUIT");
			ackObj.put("id", id);
			String ack = ackObj.toString();
			// 클라이언트한테 전송
			OutputStream out = this.socket.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
			pw.println(ack);
			pw.flush();
		}
	}
	
	// yourId에 해당하는 접속자를 찾아서 패킷을 전송
	private void unicast(String packet, String yourid) throws IOException {
		Socket sock = (Socket) ht.get(yourid);
		
		OutputStream out = sock.getOutputStream();
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
		pw.println(packet);
		pw.flush();
	}
	
	// 접속 클라이언트를 제외한 모든 접속자한테 패킷을 전송
	private void broadcast(String packet) throws IOException {
		// 현재 Hashtable에 등록된 모든 사용자의 id와 Socket을 가져온다.
		Set<String> idSet = ht.keySet();
		Iterator<String> idIter = idSet.iterator();
		while(idIter.hasNext()) {
			String id = idIter.next();
			Socket sock = (Socket) ht.get(id);
			
			// 메시지를 보내온 클라이언트한테는 보낼 필요가 없으므로
			if(sock==this.socket)
				continue;
			
			OutputStream out = sock.getOutputStream();
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
			pw.println(packet);
			pw.flush();
		}
	}
	
	private double arith(String op, double val1, double val2) {
		double result=0.;
		switch(op) {
		case "+":
			result = val1+val2;
			break;
		case "-":
			result = val1-val2;
			break;
		case "*":
			result = val1*val2;
			break;
		case "/":
			result = val1/val2;
			break;
		}
		return result;
	}
}
