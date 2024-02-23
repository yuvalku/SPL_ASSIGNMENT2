package bguspl.set.ex;

public class ReaderWriter {
    private boolean activeDealer;
    private int activePlayers;

    public ReaderWriter(){
        activePlayers = 0;
        activeDealer = false;
    }

    public synchronized void playerLock(){
        try{
            while(activeDealer){
                this.wait();
            }
        } catch (InterruptedException ignored){}
        activePlayers++;
    }

    public synchronized void playerUnlock(){
        activePlayers--;
        notifyAll();
    }

    public synchronized void dealerLock(){
        activeDealer = true;
        try{
            while(activePlayers > 0){
                this.wait();
            }
        } catch (InterruptedException e){}
    }

    public synchronized void dealerUnlock(){
        activeDealer = false;
        notifyAll();
    }
}
