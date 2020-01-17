/*
DC Project 2 part 2
Team members:
1. Venkata Sai Rohith Kilambi (vxk170007)
2. Chaitanya Kumar Malineni (cxm180003)
*/
	import java.io.File;
	import java.util.Comparator;
	import java.util.Random;
	import java.util.Scanner;
	import java.util.ArrayList;
	import java.util.Iterator;
	import java.util.concurrent.BlockingQueue;
	import java.util.concurrent.PriorityBlockingQueue;
	import java.util.concurrent.CountDownLatch;
	import java.util.concurrent.ArrayBlockingQueue;

	//This class is responsible for creating channels between two processes
	class Channel
	{
		Process p1,p2;
		
		public Channel(Process p1, Process p2)
		{
			this.p1 = p1;
			this.p2 = p2;
		}
		
		//This method returns the neighbor of the specified process
		public Process getNeighbor(Process p)
		{
			if(p == p1)
				return p2;	
			return p1;
		}
	}

	//This class is used to simulate a message
	class Message
	{
		String msg_type;	//Type is one of the following	(explore, ack, nack, leader, dummy)
		int delay;			//Time required for a message to get delivered
		Process to,from;	//To and from processes
		int id;				//ID of the sender
		
		public Message(int id,Process from, Process to, String msg_type, int delay)
		{
			this.id = id;
			this.from = from;
			this.to = to;
			this.msg_type = msg_type;
			this.delay = delay;
		}

		//Getters and Setters
		
		public int getId() {
			return id;
		}

		public Process getFrom() {
			return from;
		}
		
		public Process getTo() {
			return to;
		}

		public String getMsg_type() {
			return msg_type;
		}

		public int getDelay() {
			return delay;
		}
	}
	
	//This class is used to keep track of the number of messages sent and no of processes that are currently running in a round
	class Count
	{
		int no_of_messages;
		int no_of_proc;	//Used to keep track of the processes that completed a round
		
		public int getNo_of_proc() {
			return no_of_proc;
		}
		public void setNo_of_proc(int no_of_proc) {
			this.no_of_proc = no_of_proc;
		}
		Count()
		{
			no_of_messages=0;
		}
	}
	public class Project3
	{
		public static void main(String[] args)
		{
			Master m=new Master();	//Create a new Master Thread
			Thread master=new Thread(m);
			master.start();			//Start the thread
		}
	}
	
	//Master Thread
	class Master implements Runnable
	{
		int master_id,no_of_processes;

		boolean completed = false; 

		BlockingQueue<String> master_queue;		//Used by slave processes to notify master that it has completed the current round

		ArrayList<BlockingQueue<String>> timestamp_queue;	//Used by master process to notify slave processes to start a new round
		public Master()
		{
			
		}
		public Master(int master_id, int no_of_processes)
		{
			this.master_id = master_id;

			this.no_of_processes = no_of_processes;

			master_queue = new ArrayBlockingQueue<>(no_of_processes);

			timestamp_queue = new ArrayList<>(no_of_processes);

			String str="";

			//Initialize the master and time stamp queues
			for(int i = 0; i < no_of_processes; i++)
			{
				master_queue.add(str);
				timestamp_queue.add(new ArrayBlockingQueue<>(10));
			}
		}

		//Used to check if the processes completed the round
		public boolean check_progress()
		{
			int i,count = 0;
			String str;

			//Return false if all processes did not notify the master
			if(master_queue.size() < no_of_processes)
				return false;

			for(i = 0; i < no_of_processes; i++)
			{
				try 
				{
					str = master_queue.take();
					if(str.equals("Leader found"))	//Check if the process sent leader found message
					{
						count++;
						if(count == no_of_processes)	//If all processes sent leader found message, we can terminate
						{
							completed = true;
							return false;
						}
					}
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			return true;
		}

		//This method is used to notify processes to start a new round
		public void notify_processes()
		{
			//Iterate through the time stamp queues and add a message that indicates to start a new round
			Iterator<BlockingQueue<String>> it = timestamp_queue.iterator();
			BlockingQueue<String> b;

			while(it.hasNext())
			{
				b = it.next();
				b.add("");
			}
		}

		@Override
		public void run()
		{
			int n=0,i,j;
			int[] ids=null;
			int[][] neighbors=null;
			Scanner s=null;
			
			try
			{
				s=new Scanner(new File("connectivity.txt"));	//Read input from file
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			
			n=s.nextInt();	//Read size
			ids=new int[n];
			for(i=0;i<n;i++)
				ids[i]=s.nextInt();		//Read ids
			
			neighbors=new int[n][n];
			for(i=0;i<n;i++)
			{
				for(j=0;j<n;j++)
					neighbors[i][j]=s.nextInt();	//Get the neighbors list
			}
			
			Master master = new Master(-1, n);		//Master ID is -1
			Count c=new Count();
			c.setNo_of_proc(n);
			Process[] process = new Process[n];		
			
			
			for(i = 0; i < n; i++)
				process[i] = new Process(ids[i],c,master.master_queue,
										master.timestamp_queue.get(i));		//Create slave objects
			
			Channel channel;
			
			//Iterate through the connectivity matrix and set the channel if the element is 1
			for(i =0; i < n; i++)
			{
				for(j = 0; j <= i; j++)
				{
					if(neighbors[i][j]==1)
					{
						channel = new Channel(process[i], process[j]);
						process[i].addLink(channel);	//Add the links
						process[j].addLink(channel);
					}
				}
			}
			
			//Start the slave processes
			Thread[] t = new Thread[n];
			for(i = 0; i < n; i++)
			{
				t[i] = new Thread(process[i]);
				t[i].start();
			}
			int round=1;
			master.notify_processes();//Initially notify processes to start the first round
			
			//Loop until the leader is found
			while(!master.completed)
			{
				//Check the progress of the processes
				if(master.check_progress())
				{
					//If this is true, it means that all processes completed the current round
					if(c.getNo_of_proc()==0)
					{
						//Start new round
						round++;
						c.setNo_of_proc(n);
						//Notify processes to start the next round
						master.notify_processes();
					}
				}
			}

			//Once leader is found, interrupt the slave processes and terminate
			for(i = 0; i < n; i++)
				t[i].interrupt();

			
			for(i = 0; i < n; i++)
			{
				try
				{
					t[i].join();
				}
				catch (InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println("Flood Max algorithm completed. The total number of messages is: "+c.no_of_messages);
		}
	}

	//Slave process
	class Process implements Runnable
	{
		//Declare variables
		int id,current_timestamp=0,max_id,explore_count=0,ack_count=0,nack_count=0;
		ArrayList<Channel> neighbors;	//Contains the neighbor list

		ArrayList<Message> message_buffer = new ArrayList<Message>();//Contains all the messages that must be sent to the neighbors
		ArrayList<Process> children = new ArrayList<Process>();		//Contains all the children of a process

		BlockingQueue<String> master_queue,timestamp_queue;
		
		BlockingQueue<Message> neighbor_queue;	//This queue is used for receiving the messages from the neighbors

		Process parent;	//Each process has only one parent
		
		CountDownLatch countDownLatch;	//Used for synchronization
		
		boolean leader_found=false;
		boolean explore_complete=false;	//Used to indicate that a process completed its exploration. Used in converge cast operation
		Random random=new Random();	//Used to set delays
		Count c;

		public Process(int id,Count c,BlockingQueue<String> master_queue,BlockingQueue<String> timestamp_queue) 
		{
			this.id = id;
			this.c=c;
			this.master_queue=master_queue;
			this.timestamp_queue=timestamp_queue;
			neighbors = new ArrayList<Channel>();

			//The queue must be sorted according to the delays in the messages.
			neighbor_queue = new PriorityBlockingQueue<Message>(20, new Comparator<Message>() {
				@Override
				public int compare(Message m1, Message m2)
				{
					return m1.getDelay() - m2.getDelay();
				}
			}); 

		}

		public int getId()
		{
			return id;
		}

		//This method is used to add the link to the process
		public void addLink(Channel channel)
		{
			neighbors.add(channel);
		}

		@Override
		public void run()
		{
			//Initially the max id is its own id
			max_id = id;

			//Initialize the latch to the size of the neighbors
			countDownLatch = new CountDownLatch(neighbors.size());
			
			Message msg=null;
			String str;
			//Initially send explore messages to all its neighbors in the first round
			for(Channel channel : neighbors)
			{
				msg = new Message(id, this, channel.getNeighbor(this), "explore", (random.nextInt(10)+1));
				message_buffer.add(msg);
				explore_count++;
				c.no_of_messages++;
			}
			//Used to keep track of the visited processes in a round
			ArrayList<Process> visited = new ArrayList<Process>();
			while(true)
			{
				try
				{
					str = timestamp_queue.take();	//Take the message from the master queue indicating a new round
				}
				catch (InterruptedException e)
				{
					break;
				}
				
				//Send all the messages in the buffer to the appropriate neighbors
				for(Message m:message_buffer)
				{						
					for(Channel channel : neighbors)
					{
						if(m.getTo() == channel.getNeighbor(this))
							channel.getNeighbor(this).neighbor_queue.add(m);
					}
				}
				//Decrease count of the neighbor and wait till its countdown latch becomes 0
				for(Channel channel : neighbors)
					channel.getNeighbor(this).countDownLatch.countDown();

				try
				{
					countDownLatch.await();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}

				//Initialize it again so that it can be used for next round
				countDownLatch = new CountDownLatch(neighbors.size());
				message_buffer.clear();	//Clear the contents of the message buffer
				
				Message m1 = null;
				
				if(!neighbor_queue.isEmpty())
				{
					//Repeat until the message queue is empty
					while(neighbor_queue.size() > 0)
					{
						//If the current time stamp is equal to the delay, proceed
						if(neighbor_queue.peek().getDelay() == current_timestamp)
						{
							//Get the message
							m1 = neighbor_queue.remove();
								
							//If it is dummy, do nothing
							if(m1.getMsg_type().equals("dummy"))
							{
								
							}
							
							//If it is explore 
							if(m1.getMsg_type().equals("explore"))
							{
								//If the id is greater than max id
								if(m1.getId() > max_id)
								{
									//Send nack to parent if it has one
									if(parent != null)
									{
										msg = new Message(Integer.MIN_VALUE, this, parent, "nack", (random.nextInt(10)+1));
										message_buffer.add(msg);
										c.no_of_messages++;
										visited.add(parent);
									}
									//Set the max id and the parent
									max_id = m1.getId();
									parent = m1.getFrom();

									//Send explore message to all neighbors except its parent
									for(Channel channel : neighbors)
									{
										if(channel.getNeighbor(this) != parent)
										{
											msg = new Message(max_id, this, channel.getNeighbor(this), "explore", (random.nextInt(10)+1));
											message_buffer.add(msg);
											explore_count++;
											c.no_of_messages++;
											visited.add(channel.getNeighbor(this));
										}
									}
								}
								//If the id is less than max id, send nack to the sender
								else if(m1.getId() < max_id)
								{
									msg = new Message(Integer.MIN_VALUE, this, m1.getFrom(), "nack", (random.nextInt(10)+1));
									message_buffer.add(msg);
									visited.add(m1.getFrom());
									c.no_of_messages++;
								}
								//If it is equal, it means that the message came from a different path. So send nack to the sender
								else
								{
									if(m1.getId() == m1.getFrom().getId())
										msg = new Message(Integer.MIN_VALUE, this,m1.getFrom(), "ack", (random.nextInt(10)+1));
									else
										msg = new Message(Integer.MIN_VALUE, this, m1.getFrom(), "nack", (random.nextInt(10)+1));
									message_buffer.add(msg);
									c.no_of_messages++;
									visited.add(m1.getFrom());
								}
							}
							
							//If it is ack, increment ack count and add the sender as its child
							else if(m1.getMsg_type().equals("ack"))
							{
								ack_count++;
								children.add(m1.getFrom());
							}

							//If it is nack, increment nack count
							else if(m1.getMsg_type().equals("nack"))
								nack_count++;
								
							//If it is leader, send leader announcement to its children
							else if(m1.getMsg_type().equals("leader"))
							{
								System.out.println("ID: "+id+" Leader: "+m1.getId());
								for(Process child : children)
								{
									for(Channel channel : neighbors)
									{
										if(channel.getNeighbor(this) == child)
										{
											msg = new Message( m1.getId(), this, child, "leader", (random.nextInt(10)+1));
											message_buffer.add(msg);
											c.no_of_messages++;
											visited.add(child);
										}
									}
								}
								leader_found = true;	//Set leader found to true to indicate that this process has found the leader
							}
						}
						//If the delay is not equal to the time stamp, then increment time stamp
						else
							current_timestamp++;
					}
				}
				
				//Converge cast
				//If the explore count is equal to the sum of nack and ack count, it is an internal node
				//If the explore count is equal to the nack count, it is a leaf node
				//In any of the case, send ack to the parent
				if(((explore_count == (nack_count + ack_count) && nack_count > 0) || (explore_count == nack_count))  && !explore_complete)
				{
					for(Channel channel : neighbors)
					{
						if(channel.getNeighbor(this) == parent)
						{
							msg = new Message(Integer.MIN_VALUE, this, parent, "ack", (random.nextInt(10)+1));
							message_buffer.add(msg);
							explore_complete = true;
							c.no_of_messages++;
							visited.add(parent);
						}
					}
				}
				
				//If the explore count is equal to the ack count, it is the leader
				//Send leader announcement to the children
				else if(explore_count == ack_count && !leader_found)
				{
					System.out.println("ID: "+id+" Leader: "+id);
					for(Process child : children)
					{
						for(Channel channel : neighbors)
						{
							if(channel.getNeighbor(this) == child)
							{
								msg = new Message(id, this, child, "leader", (random.nextInt(10)+1));
								message_buffer.add(msg);
								c.no_of_messages++;
								visited.add(child);
							}
						}
					}
					leader_found = true;
				}
				
				//Send dummy messages to all the neighbors except for the ones that the process sent the real message (explore, ack, nack, leader)
				for(Process v : visited)
				{
					for(Channel channel : neighbors)
					{
						if(channel.getNeighbor(this) != v)
						{
							msg = new Message(id, this, channel.getNeighbor(this), "dummy", (random.nextInt(10)+1));
							message_buffer.add(msg);
						}
					}
				}
				
				//Decrease count to indicate this process has completed the current round
				c.setNo_of_proc(c.getNo_of_proc()-1);
				str="";
				current_timestamp=0;	//Reset the time stamp to 0
				if(leader_found)	//If the leader is found, send Leader found to the master, otherwise send a blank message
					str = "Leader found";

				try
				{
					master_queue.put(str);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}