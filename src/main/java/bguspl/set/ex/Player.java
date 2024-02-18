package bguspl.set.ex;

import java.util.Random;

import bguspl.set.Env;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    // Added
    private int tokenCounter;
    private Dealer dealer;
    private actionsQueue<Integer> inActions;
    private boolean toScore;


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        // Added
        this.dealer = dealer;
        terminate = false;
        score = 0;
        tokenCounter = 0;
        inActions = new actionsQueue<Integer>();
        toScore = false;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop

            int slot = inActions.take();
            
            //if there is a card in this place on the table
            if (table.getCard(slot) != null){

                // place or remove token
                if (table.removeToken(id, slot)){
                    tokenCounter--;
                }
                else {
                    table.placeToken(id, slot);
                    tokenCounter++;
                }

                if (tokenCounter == 3){

                    // extract the set and create triple for the dealer
                    int[][] set = table.returnSet(id);
                    int[] setCards = set[0];
                    int[] setSlots = set[1];
                    Triple<Integer, int[], int[]> triple = new Triple(id, setCards, setSlots);
                    dealer.pushToTestSet(triple);

                    // wait until dealer responds
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // TODO: handle exception
                    }

                    // point or penalty and clear queue
                    if (toScore)
                        point();
                    else
                        penalty();
                    inActions.clearQueue();
                }
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator

                Random random = new Random();
                int slot = random.nextInt(12);
                inActions.put(slot);
                
                // try {
                //     synchronized (this) { wait(); }
                // } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement

        inActions.put(slot);

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() { 
        // TODO implement
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        long time_left = env.config.pointFreezeMillis;
        long time = System.currentTimeMillis();

        while(time_left > 0){
            env.ui.setFreeze(id, time_left);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {}

            //update time left
            time_left = env.config.pointFreezeMillis - (System.currentTimeMillis() - time);  
        }
        env.ui.setFreeze(id, 0);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement

        long time_left = env.config.penaltyFreezeMillis;
        long time = System.currentTimeMillis();
        
        while(time_left > 0){

            env.ui.setFreeze(id, time_left);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {}
            time_left = env.config.pointFreezeMillis - (System.currentTimeMillis() - time);
        }
        
        env.ui.setFreeze(id, 0);
        


    }

    public int score() {
        return score;
    }
    
    //Added
    public void toScore(boolean toscore) {
        toScore = toscore;
        // To interrupt player?
    }

    public void removeToken(int slot){
        if (table.removeToken(id, slot))
            tokenCounter--;
    }
}
