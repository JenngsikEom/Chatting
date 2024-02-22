package jeongsik;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;


public class Client {
	
	// 클라이언트가 특정 메뉴를 진행하는 상태
	public static ClientState mainState = ClientState.NONE;

	public static void main(String[] args) {
		String IP = "127.0.0.1";
		int PORT = 9000;
		String id = "";
		Socket socket = null;
		PrintWriter pw = null;
		BufferedReader br = null;
		Scanner scan = new Scanner(System.in);
		
		System.out.printf("기본 IP=%s, 기본 PORT=%d\n", IP, PORT);
		System.out.print("IP, Port 설정필요하십니까?(y/yes/예, 아니면 그냥 Enter) >> ");
		String answer = scan.nextLine();
		if(answer.equals("y") || answer.equals("yes") || answer.equals("예")) {
			System.out.print("접속할 IP >> ");
			IP = scan.nextLine();
			System.out.print("접속할 Port >> ");
			PORT = Integer.parseInt(scan.nextLine());
		}
		try {
			// 서버를 주소를 찾아서 연결
			socket = new Socket(IP, PORT);
			pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//			br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			
			// 별도의 worker thread를 생성해서 서버로부터의 수신을 담당한다.
			ReceiveThread rt = new ReceiveThread(br);
			rt.start();
			
			// main thread는 서버에 전송을 담당한다.
			// 1) id를 서버에 등록한다
			
			id = sendId(scan, pw);
			Thread.sleep(300);
			
			// 2) 메뉴를 선택해서 원하는 요청을 서버로 보낸다.
			boolean isRun = true;
			while(isRun) {
				if(Client.mainState!=ClientState.NONE) {
					Thread.sleep(10);
					continue;
				}	
				int sel = getSelectMenu(scan);
				switch(sel) {
				case Menu.ALL_CHAT:
					sendAllChat(scan, pw, id);
					break;
				case Menu.ONE_CHAT:
					sendOneChat(scan, pw, id);
					break;
				case Menu.CALC_ARITH:
					sendArith(scan, pw, id);
					break;
				case Menu.EXIT:
					sendQuit(pw, id);
					isRun = false;
					break;
				default:
					break;
				}
			}
			System.out.println("Client 프로그램 종료~");
			
		}catch(Exception e) {
			System.out.println(e.getMessage());
		}finally {
			if(socket!=null)
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}		
			if(scan!=null) scan.close();
		}	
	}
	
	public static void sendQuit(PrintWriter pw, String id) {
		JSONObject packetObj = new JSONObject();
		packetObj.put("cmd", "QUIT");
		packetObj.put("id", id);
		
		String packet = packetObj.toString();
		
		pw.println(packet);
		pw.flush();	
	}
	
	public static void sendReqIdList(PrintWriter pw, String id) {
		JSONObject packetObj = new JSONObject();
		packetObj.put("cmd", "REQIDLIST");
		packetObj.put("id", id);
		
		String packet = packetObj.toString();
		
		pw.println(packet);
		pw.flush();	
	}
	
	public static void sendOneChat(Scanner sc, PrintWriter pw, String id) throws InterruptedException {
		Client.mainState = ClientState.STATE_REQIDLIST;
		sendReqIdList(pw, id);
		boolean isRun = true;
		while(isRun) {
			if(Client.mainState == ClientState.STATE_ONE_CHAT_QUIT) {
				isRun = false;
				Client.mainState = ClientState.NONE;
				System.out.println("1:1 탈출 - 2");
				break;
			}
			if(Client.mainState != ClientState.STATE_ONE_CHAT_1) {
				Thread.sleep(10);
				continue;
			}
			
			Thread.sleep(200);
			System.out.print("전송할 id >> ");
			String yourid = sc.nextLine();
			Client.mainState = ClientState.STATE_ONE_CHAT_2;
			boolean isRun2 = true;
			while(isRun2) {
				System.out.print("전송 메시지 (quit는 종료) >> ");
				String msg = sc.nextLine();
				if(msg.equals("quit")) {
					isRun2 = false;
					Client.mainState = ClientState.STATE_ONE_CHAT_QUIT;
					System.out.println("1:1 탈출 - 1");
					break;
				}				
				
				JSONObject packetObj = new JSONObject();
				packetObj.put("cmd", "ONECHAT");
				packetObj.put("id", id);
				packetObj.put("yourid", yourid);
				packetObj.put("msg", msg);
				
				String packet = packetObj.toString();
				
				pw.println(packet);
				pw.flush();					
			}
		
		}		
	}	
	public static void sendAllChat(Scanner sc, PrintWriter pw, String id) throws InterruptedException {
		Client.mainState = ClientState.STATE_ALL_CHAT;
		boolean isRun = true;
		while(isRun) {
			
			Thread.sleep(200);
			System.out.print("전송 메시지 (quit는 종료) >> ");
			String msg = sc.nextLine();
			if(msg.equals("quit")) {
				isRun = false;
				Client.mainState = ClientState.NONE;
				break;
			}							
			JSONObject packetObj = new JSONObject();
			packetObj.put("cmd", "ALLCHAT");
			packetObj.put("id", id);
			packetObj.put("msg", msg);
			
			String packet = packetObj.toString();
			
			pw.println(packet);
			pw.flush();			
		}
	}
	
	 /*   3-2) 사칙 연산
	 *     [요청]
	 *     cmd:ARITH
	 *     id:{id값}
	 *     op:{연산자}
	 *     val1:{첫번째값}
	 *     val2:{두번째값}
	 *     
	 *     [응답]
	 *     cmd:ARITH
	 *     ack:{결과값}
	 */
	public static void sendArith(Scanner sc, PrintWriter pw, String id){
		Client.mainState = ClientState.STATE_ARITH;
		System.out.print("연산자 입력(+ - * /) >> ");
		String op = sc.nextLine();
		System.out.print("첫번째 숫자 입력 >> ");
		String num1 = sc.nextLine();
		System.out.print("두번째 숫자 입력 >> ");
		String num2 = sc.nextLine();
		
		JSONObject packetObj = new JSONObject();
		packetObj.put("cmd", "ARITH");		
		packetObj.put("id", id);
		packetObj.put("op", op);
		packetObj.put("val1", num1);
		packetObj.put("val2", num2);
		
		String packet = packetObj.toString();
		
		pw.println(packet);
		pw.flush();
	}
	
	public static int getSelectMenu(Scanner sc) {
		System.out.println();
		System.out.println("------------------------");
		System.out.println("1. 전체 채팅");
		System.out.println("2. 1:1 채팅");
		System.out.println("3. 사칙연산");
		System.out.println("4. exit");
		System.out.print("번호 입력 >> ");
		int sel = Integer.parseInt(sc.nextLine());
		return sel;
	}
	
	 /*   <id등록>
	 *     [요청]
	 *     cmd:ID
	 *     id:{id값}
	 *     
	 *     [응답]
	 *     cmd:ID
	 *     ack:ok(성공), fail(실패)
	 */
	public static String sendId(Scanner sc, PrintWriter pw) {
		Client.mainState = ClientState.STATE_ID;
		System.out.print("당신의 id 입력 >> ");
		String id = sc.nextLine();
		
		// 프로토콜(약속)에 의한 요청 패킷 구성
		JSONObject idObj = new JSONObject();
		idObj.put("cmd", "ID");
		idObj.put("id", id);
		// 문자열 변환
		String packet = idObj.toString();
		pw.println(packet);
		pw.flush();
		
		return id;
	}
}

class ReceiveThread extends Thread{
	private BufferedReader br = null;
	
	public ReceiveThread(BufferedReader br) {
		this.br = br;
	}
	@Override
	public void run() {
		try {			
			while(true) {
				String packet = br.readLine();
				if(packet==null)
					break;
				
				JSONObject packetObj = new JSONObject(packet);
				processPacket(packetObj);
			}
			
		}catch(Exception e) {
			System.out.println(e.getMessage());
		}
	}
	public void processPacket(JSONObject packetObj) throws UnsupportedEncodingException {
		String preCmd = packetObj.getString("cmd");
		String cmd = new String(preCmd.getBytes("utf-8"), "utf-8");
		// 서버의 id처리에 대한 응답
		if(Client.mainState==ClientState.STATE_ID && cmd.equals("ID")) {
			String ack = packetObj.getString("ack");
			if(ack.equals("ok"))
				System.out.println("[서버 응답] ID 등록 성공");
			else if(ack.equals("fail"))
				System.out.println("[서버 응답] ID 등록 실패");
			else
				System.out.printf("[서버 응답] ID 등록 %s\n", ack);
			
			Client.mainState = ClientState.NONE;
		}
		// 서버의 사칙연산 결과 응답
		else if(Client.mainState==ClientState.STATE_ARITH && cmd.equals("ARITH")) {
			String ack = packetObj.getString("ack");			
			System.out.println("[서버 응답] 연산 결과=" + ack);			
			
			Client.mainState = ClientState.NONE;
		}
		// 서버의 전송 채팅에 대한 응답
		else if(Client.mainState==ClientState.STATE_ALL_CHAT && cmd.equals("ALLCHAT")) {
			String ack = packetObj.getString("ack");
			if(ack.equals("ok"))
				System.out.println("[서버 응답] ALL 채팅 전송 성공");
			else if(ack.equals("fail"))
				System.out.println("[서버 응답] ALL 채팅 전송 실패");
			else
				System.out.printf("[서버 응답] ALL 채팅 전송 %s\n", ack);
		}
		// 서버의 1:1 채팅에 대한 응답
		else if(Client.mainState==ClientState.STATE_ONE_CHAT_1 && cmd.equals("ONECHAT")) {
			String ack = packetObj.getString("ack");
			if(ack.equals("ok"))
				System.out.println("[서버 응답] 1:1 채팅 전송 성공");
			else if(ack.equals("fail"))
				System.out.println("[서버 응답] 1:1 채팅 전송 실패");
			else
				System.out.printf("[서버 응답] 1:1 채팅 전송 %s\n", ack);
		}
		// 다른 사람의 채팅 메시지를 서버가 전송
		else if(Client.mainState==ClientState.STATE_ALL_CHAT && cmd.equals("BROADCHAT")) {
			String id = packetObj.getString("id");
			
			String msg = packetObj.getString("msg");
			System.out.printf("\n전체 채팅 메시지 [%s] %s\n", id, msg);
		}
		// 특정 사람이 나의 id로 보낸 메시지 서버가 전송
		else if((Client.mainState==ClientState.STATE_ONE_CHAT_2 || Client.mainState==ClientState.STATE_ALL_CHAT) && cmd.equals("UNICHAT")) {
			String id = packetObj.getString("id");
			String msg = packetObj.getString("msg");
			System.out.printf("\n>>>>>>>>>>>>> 1:1 채팅 메시지 [%s -> ] %s\n", id, msg);
		}
		// 서버한테 전체 접속자 id 리스트 요청
		else if(Client.mainState==ClientState.STATE_REQIDLIST && cmd.equals("REQIDLIST")) {
			JSONArray idList = packetObj.getJSONArray("list");
			for(int i=0;i<idList.length();i++) {
				System.out.printf("접속 ID 목록 : %d. %s\n", i+1, idList.get(i));
			}

			Client.mainState = ClientState.STATE_ONE_CHAT_1;
		}
		else if((Client.mainState==ClientState.STATE_QUIT) && cmd.equals("QUIT")) {
			String id = packetObj.getString("id");

			System.out.printf("Id=%s QUIT 메시지 응답\n", id);
		}
	}
}
