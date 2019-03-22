import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DataGenerator implements Runnable{
	
	private RepositoryHandler repository = null;
	//Used to have an exponential distribution of the interarrivals of the data collected
	private Random exponentialSeed = new Random(); //Used to generate a uniform distribution between 0 and 1
	private double lambda = 0.5; //mean rate of arrival of a message
	//Used for debug purposes
	private int dataGenerated = 0;
	private int numberOfWrites;
	
	public DataGenerator(RepositoryHandler repository, int numberOfWrites) {
		this.repository = repository;
		this.numberOfWrites = numberOfWrites;
	}

	public void run(){
		Random gaussian = new Random();
		for (int i = 0; i < 10; i++) {
			double delay = getNextExponentialDelay();
			System.out.println(Thread.currentThread().getName() + " should wait " + (long)delay);
			try
			{
			    //Thread.sleep((long)delay*1000);
				TimeUnit.SECONDS.sleep((long) delay);
			}
			catch(InterruptedException ex)
			{
				System.err.println(Thread.currentThread().getName() + " has been interrupted!");
			    Thread.currentThread().interrupt();
			}
			Double sensedData = gaussian.nextGaussian();
			dataGenerated++;
			try {
				//System.out.println(Thread.currentThread().getName() + " have generated " + dataGenerated + " values");
				repository.write(sensedData);
			} catch (InterruptedException e) {
				System.out.println(e.getMessage());
			}
		}
	}
	
	public double getNextExponentialDelay() {
	    double result =  Math.log(1-exponentialSeed.nextDouble())/(-lambda);
	    if(result < 0)
	    	result = 0;
	    return result;
	}



}
