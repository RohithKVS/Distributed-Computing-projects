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

	class Channel
	{
		Process p1,p2;
		
		public Channel(Process p1, Process p2)
		{
			this.p1 = p1;
			this.p2 = p2;
		}
		
		public Process getNeighbor(Process p)
		{
			if(p == p1)
				return p2;	
			return p1;
		}
	}

	class Message
	{
		String msg_type;
		int delay;
		Process to,from;
		int id;
		
		public Message(int id,Process from, Process to, String msg_type, int delay)
		{
			this.id = id;
			this.from = from;
			this.to = to;
			this.msg_type = msg_type;
			this.delay = delay;
		}

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
	
	class Count
	{
		int no_of_messages;
		Count()
		{
			no_of_messages=0;
		}
	}
	public class Project2
	{
		public static void main(String[] args)
		{
			Master m=new Master();
			Thread master=new Thread(m);
			master.start();
		}
	}
	class Master implements Runnable
	{
		int master_id,no_of_processes;

		boolean completed = false; 

		BlockingQueue<String> master_queue;

		ArrayList<BlockingQueue<String>> timestamp_queue;
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

			for(int i = 0; i < no_of_processes; i++)
			{
				master_queue.add(str);
				timestamp_queue.add(new ArrayBlockingQueue<>(10));
			}
		}

		public boolean check_progress()
		{
			int i,count = 0;
			String str;

			if(master_queue.size() < no_of_processes)
				return false;

			for(i = 0; i < no_of_processes; i++)
			{
				try 
				{
					str = master_queue.take();
					if(str.equals("Leader found"))
					{
						count++;
						if(count == no_of_processes)
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

		public void notify_processes()
		{
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
				s=new Scanner(new File("connectivity.txt"));
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			
			n=s.nextInt();
			ids=new int[n];
			for(i=0;i<n;i++)
				ids[i]=s.nextInt();
			
			neighbors=new int[n][n];
			for(i=0;i<n;i++)
			{
				for(j=0;j<n;j++)
					neighbors[i][j]=s.nextInt();
			}
			
			Master master = new Master(-1, n);

			Process[] process = new Process[n];
			Count c=new Count();
			
			for(i = 0; i < n; i++)
				process[i] = new Process(ids[i],c,master.master_queue,
										master.timestamp_queue.get(i));
			
			Channel channel;
			for(i =0; i < n; i++)
			{
				for(j = 0; j <= i; j++)
				{
					if(neighbors[i][j]==1)
					{
						channel = new Channel(process[i], process[j]);
						process[i].addLink(channel);
						process[j].addLink(channel);
					}
				}
			}
			
			Thread[] t = new Thread[n];
			for(i = 0; i < n; i++)
			{
				t[i] = new Thread(process[i]);
				t[i].start();
			}

			while(!master.completed)
			{
				if(master.check_progress())
					master.notify_processes();
			}

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

	class Process implements Runnable
	{
		int id,current_timestamp=0,max_id,explore_count=0,ack_count=0,nack_count=0;
		ArrayList<Channel> neighbors;

		ArrayList<Message> message_buffer = new ArrayList<Message>();
		ArrayList<Process> children = new ArrayList<Process>();

		BlockingQueue<String> master_queue,timestamp_queue;
		
		BlockingQueue<Message> neighbor_queue;

		Process parent;
		CountDownLatch countDownLatch;
		boolean leader_found=false;
		boolean explore_complete=false;
		Random random=new Random();
		Count c;

		public Process(int id,Count c,BlockingQueue<String> master_queue,BlockingQueue<String> timestamp_queue) 
		{
			this.id = id;
			this.c=c;
			this.master_queue=master_queue;
			this.timestamp_queue=timestamp_queue;
			neighbors = new ArrayList<Channel>();

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

		public void addLink(Channel channel)
		{
			neighbors.add(channel);
		}

		@Override
		public void run()
		{
			max_id = id;

			countDownLatch = new CountDownLatch(neighbors.size());
			
			Message msg=null;
			String str;
			for(Channel channel : neighbors)
			{
				msg = new Message(id, this, channel.getNeighbor(this), "explore", (random.nextInt(10)+1)+current_timestamp);
				message_buffer.add(msg);
				explore_count++;
				c.no_of_messages++;
			}
			
			while(true)
			{
				
				try
				{
					str = timestamp_queue.take();
				}
				catch (InterruptedException e)
				{
					break;
				}

				for(Message m:message_buffer)
				{						
					for(Channel channel : neighbors)
					{
						if(m.getTo() == channel.getNeighbor(this))
							channel.getNeighbor(this).neighbor_queue.add(m);
					}
				}

				for(Channel channel : neighbors)
					channel.getNeighbor(this).countDownLatch.countDown();;

				try
				{
					countDownLatch.await();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}

				countDownLatch = new CountDownLatch(neighbors.size());
				message_buffer.clear();

				Message m1 = null;

				if(!neighbor_queue.isEmpty())
				{
					while(neighbor_queue.size() > 0)
					{
						if(neighbor_queue.peek().getDelay() == current_timestamp)
						{
							m1 = neighbor_queue.remove();
								
							if(m1.getMsg_type().equals("explore"))
							{
								if(m1.getId() > max_id)
								{
									if(parent != null)
									{
										msg = new Message(Integer.MIN_VALUE, this, parent, "nack", (random.nextInt(10)+1)+current_timestamp);
										message_buffer.add(msg);
										c.no_of_messages++;
									}
									max_id = m1.getId();
									parent = m1.getFrom();

									for(Channel channel : neighbors)
									{
										if(channel.getNeighbor(this) != parent)
										{
											msg = new Message(max_id, this, channel.getNeighbor(this), "explore", (random.nextInt(10)+1)+current_timestamp);
											message_buffer.add(msg);
											explore_count++;
											c.no_of_messages++;
										}
									}
								}
								else if(m1.getId() < max_id)
								{
									msg = new Message(Integer.MIN_VALUE, this, m1.getFrom(), "nack", (random.nextInt(10)+1)+current_timestamp);
									message_buffer.add(msg);
									c.no_of_messages++;
								}
								else
								{
									if(m1.getId() == m1.getFrom().getId())
										msg = new Message(Integer.MIN_VALUE, this,m1.getFrom(), "ack", (random.nextInt(10)+1)+current_timestamp);
									else
										msg = new Message(Integer.MIN_VALUE, this, m1.getFrom(), "nack", (random.nextInt(10)+1)+current_timestamp);
									message_buffer.add(msg);
									c.no_of_messages++;
								}
							}
							
							else if(m1.getMsg_type().equals("ack"))
							{
								ack_count++;
								children.add(m1.getFrom());
							}

							else if(m1.getMsg_type().equals("nack"))
								nack_count++;
								
							else if(m1.getMsg_type().equals("leader"))
							{
								System.out.println("ID: "+id+" Leader: "+m1.getId());
								for(Process child : children)
								{
									for(Channel channel : neighbors)
									{
										if(channel.getNeighbor(this) == child)
										{
											msg = new Message( m1.getId(), this, child, "leader", (random.nextInt(10)+1)+current_timestamp);
											message_buffer.add(msg);
											c.no_of_messages++;
										}
									}
								}
								leader_found = true;
							}
						}
						else
							break;
					}
				}

				if(((explore_count == (nack_count + ack_count) && nack_count > 0) || (explore_count == nack_count))  && !explore_complete)
				{
					for(Channel channel : neighbors)
					{
						if(channel.getNeighbor(this) == parent)
						{
							msg = new Message(Integer.MIN_VALUE, this, parent, "ack", (random.nextInt(10)+1)+current_timestamp);
							message_buffer.add(msg);
							explore_complete = true;
							c.no_of_messages++;
							break;
						}
					}
				}
				else if(explore_count == ack_count && !leader_found)
				{
					System.out.println("ID: "+id+" Leader: "+id);
					for(Process child : children)
					{
						for(Channel channel : neighbors)
						{
							if(channel.getNeighbor(this) == child)
							{
								msg = new Message(id, this, child, "leader", (random.nextInt(10)+1)+current_timestamp);
								message_buffer.add(msg);
								c.no_of_messages++;
							}
						}
					}
					leader_found = true;
				}
				current_timestamp++;
				str="";
				if(leader_found)
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