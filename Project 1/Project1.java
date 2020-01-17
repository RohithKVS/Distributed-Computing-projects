/*

DC Project 1
Team members:
1. Venkata Sai Rohith Kilambi (vxk170007)
2. Chaitanya Kumar Malineni (cxm180003)

*/
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Project1
{
	public static void main(String[] args) throws FileNotFoundException, InterruptedException
	{
		Master m=new Master();	//Create a new Master Thread
		Thread master=new Thread(m);
		master.start();			//Start the thread
	}
}

//This class keeps track of all the information shared between the slave threads.
class Count
{
	int count;	//Used to keep track of the processes that completed a round
	int no_of_processes;	
	int[] ids;
	int[] previous;		//Used to keep track of the smallest id seen so far
	int leader;
	int round;
	boolean leader_found=false;
	//Used for synchronization
	Lock lock=new ReentrantLock();
	Condition master=lock.newCondition();
	Condition slave=lock.newCondition();
	HashMap<Integer,Integer> previous_index=new HashMap<>();	//Used to keep the track of which process is next to a given process in the ring
	//Getters and Setters for all the variables
	
	public int getRound() {
		return round;
	}

	public void setRound(int round) {
		this.round = round;
	}

	public int getPrev_ids(int index) {
		return previous[index];
	}

	public void setPrev_ids(int[] prev_ids) {
		this.previous = prev_ids;
	}

	public int getNo_of_processes() {
		return no_of_processes;
	}

	public void setNo_of_processes(int no_of_processes) {
		this.no_of_processes = no_of_processes;
	}
	public int getId(int index) {
		return ids[index];
	}

	public void setIds(int[] ids) {
		this.ids= ids;
	}
	
	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	//Used to set the smallest id seen so far
	public void setOnePrev_ids(int index, int min_id) {
		previous[index]=min_id;
	}
}
//Master Thread
class Master implements Runnable
{
	@Override
	public void run()
	{
		Scanner sc=null;
		try
		{
			sc = new Scanner(new File("input.dat"));	//Read input from file
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		int no_of_processes=sc.nextInt();	//Read size
		int[] arr_ids=new int[no_of_processes];
		int i;
		for(i=0;i<no_of_processes;i++)
			arr_ids[i]=sc.nextInt();	//Read ids into array
		Count count=new Count();

		count.setIds(arr_ids);	//Set the parameters that can be shared
		int prev[]=arr_ids.clone();
		count.setPrev_ids(prev);
		count.setCount(no_of_processes);
		count.setNo_of_processes(no_of_processes);
		for(i=0;i<no_of_processes;i++)
		{
			count.previous_index.put(i, i-1>=0?i-1:no_of_processes-1);
		}
		int round=1;	//Round 1
		count.setRound(round);
		for(i=0;i<no_of_processes;i++)
			new Thread(new Slave(count,i)).start();		//Create new threads
		
		try
		{
			while(true)
			{
				count.lock.lock();
				while(count.getCount()>0)	//Wait till all processes complete previous round
					count.master.await();
				count.setCount(no_of_processes);	//Reset parameter
				round++;							//Increment round
				count.setRound(round);				
				count.slave.signalAll();			//Signal all threads that a round has been started
				count.lock.unlock();
				if(count.leader_found)		//If leader is found break the loop
				{
					break;
				}
			}
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
		
	}
}

//Slave thread(Process)
class Slave implements Runnable
{
	Count count;
	int index;
	Slave(Count count,int index)
	{
		this.count=count;
		this.index=index;
	}
	public void run()
	{
		int id=count.getId(index);	//Get its own id
		int min_id=id;	//Initially min id is its own id
		Thread.currentThread().setName(id+"");
		int wait,current_round;
		
		while(true)
		{
			if(count.leader_found)		//If leader is found break the loop
			{
				count.lock.lock();
				count.slave.signalAll();
				count.master.signal();
				count.lock.unlock();
				break;
			}
			count.lock.lock();
			current_round=count.getRound();		//Get current round

			int previous_index=count.previous_index.get(index); //Get the previous process index
			int previous_id=count.getPrev_ids(previous_index);		//Get the smallest id seen so far by the previous process since this is the token that the previous process forwards
			wait=(int) Math.pow(2, previous_id);
			
			int offset=current_round/wait;	//Calculate the multiplying factor so that the waiting time is kept in sync with the current round
			if(current_round>wait)
			{
				if((offset*wait)-current_round==0) //For eg if wait time is 2 and current round is 4 make wait time equal to 4 in order to pass it to the next process in the current round
				{
					wait=wait*offset;
				}
				else		//For eg if wait time is 2 and current round is 3 make wait time equal to 4 in order to pass it to the next process when current round becomes 4
				{
					wait=wait*(offset+1);
				}
			}
			if(wait==current_round)
			{
				if(previous_id==id)	//Token came back without being dropped anywhere in the ring
				{
					count.leader=min_id;
					count.leader_found=true;
					count.setCount(0);
				}
				if(previous_id<min_id)	//If it is less that the minimum seen so far, forward it to the next process at appropriate round
				{
					min_id=previous_id;
					wait=(int) Math.pow(2, min_id);
					count.setOnePrev_ids(index,previous_id);
				}
			}
			count.setCount(count.getCount()-1);		//Decrease count to indicate this process has completed one round
			count.lock.unlock();
			try
			{
				count.lock.lock();
				while(count.getCount()>0)	//Wait till all processes complete their round
					count.slave.await();
				count.slave.signalAll();	//Used by process finishing last(in a round) to signal all other processes that it has completed
				count.master.signal();		//Signal the master that a round is completed
				count.slave.await();		//Wait for the master to signal the start of next round
				count.lock.unlock();
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		System.out.println("ID: "+count.getId(index)+" Leader: "+count.getPrev_ids(index));	//Print the ID and ID of the leader(smallest id seen so far will eventually be the leader) once a leader is found
	}
}