package bguspl.set.ex;

public class ReaderWriter {
    boolean activeDealer;
    int activePlayers;
    boolean WaitingDealer;

    public ReaderWriter(){
        activePlayers = 0;
        activeDealer = false;
        
    }

    public synchronized void playerLock(){
        while(!allowPlayer()){
            try{
                this.wait();
            } catch (InterruptedException ignored){}
        }
        activePlayers++;
    }

    public synchronized void playerUnlock(){
        activePlayers--;
        notifyAll();
    }

    public synchronized void dealerLock(){
        WaitingDealer = true;
        while(!allowDealer()){
            try{
                wait();
            } catch (InterruptedException e){}
        }
    }

    public synchronized void dealerUnlock(){
        activeDealer = false;
        notifyAll();
    }

    protected boolean allowPlayer(){
        return !activeDealer;
    }

    protected boolean allowDealer(){
        return activePlayers == 0;
    }
}
