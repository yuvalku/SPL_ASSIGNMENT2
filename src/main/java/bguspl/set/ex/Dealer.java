package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    // Added
    protected setsQueue setQ;
    private Thread dealerThread;
    private Thread[] playersThreads;
    protected Object[] locks;
    private int[] slotsOrder;
    private Random rand;
    private boolean firstSleep;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        //added
        terminate = false;
        playersThreads = new Thread[env.config.players];
        setQ = new setsQueue();
        locks = new Object[env.config.players];
        for (int i = 0; i < locks.length; i++){
            locks[i] = new Object();
        }
        slotsOrder = new int[env.config.tableSize];
        for (int i = 0; i < slotsOrder.length; i++){
            slotsOrder[i] = i;
        }
        rand = new Random();
        firstSleep = true;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {

        // Added
        dealerThread = Thread.currentThread();

        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        // create and run the player threads
        for (int i = 0; i < playersThreads.length; i++){
            playersThreads[i] = new Thread(players[i]);
        }
        for (int i = 0; i < playersThreads.length; i++){
            playersThreads[i].start();
        }

        while (!shouldFinish()) {

            // Added
            Collections.shuffle(deck);
            
            //in order to place the cards in random order on table
            shuffleArray(slotsOrder);
            
            updateTimerDisplay(true);
            placeCardsOnTable();
            //allow players to place tokens on table
            table.setCanPlaceToken(true);
            timerLoop();
            table.setCanPlaceToken(false);
            removeAllCardsFromTable();
        }

        announceWinners();

        // terminate all players threads and wait for them to join
        for (int i = playersThreads.length - 1; i >= 0; i--){
            players[i].terminate();
            playersThreads[i].interrupt();
            try{
                playersThreads[i].join();
            } catch (InterruptedException e) {}
        }

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {

        // Added
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        firstSleep = true;

        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        env.ui.dispose();
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {

        Triple<Integer, int[], int[]> toCheck = setQ.take();

        while (toCheck != null){

            // extract data from the triple
            int playerId = toCheck.getFirst();
            int[] cards = toCheck.getSecond();
            int[] slots = toCheck.getThird();

            boolean toUpdateTimer = false;

            // check if the cards in the set are still on the table
            if (table.isSetRelevant(cards, slots)){

                // check if legal set and give penalty or point
                boolean legalSet = env.util.testSet(cards);
                toUpdateTimer = legalSet;
                players[playerId].toScore(legalSet);

                // remove the cards from the table if the set was legal
                if (legalSet){
                    for (int j = 0; j < slots.length; j++){
                        table.removeCard(slots[j]);

                        // remove all the tokens from the removed cards
                        table.rw.dealerLock();
                        for (int i = 0; i < players.length; i++){
                            players[i].removeToken(slots[j]);
                        }
                        table.rw.dealerUnlock();
                    }
                    shuffleArray(slotsOrder);
                }
            }
            else 
                players[playerId].toScore(null);

            // wake player and pop from queue
            synchronized(locks[playerId]) {
                players[playerId].needToWait = false;
                locks[playerId].notifyAll();
            }

            if (toUpdateTimer) {
                updateTimerDisplay(true);
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            } 
            toCheck = setQ.take();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        
        // to know if new cards were placed
        int deckSize = deck.size();

        // For each slot that equals null, remove the first card in the deck and place it on the table in random order
        for (int i = 0; i < slotsOrder.length && deck.size() > 0; i++){
            //if there is no card in this place
            if (table.slotToCard[slotsOrder[i]] == null){
                //put a new card
                int card = deck.remove(0);
                table.placeCard(card, slotsOrder[i]); 
            }
        }

        // if new cards were placed, present hints
        if (env.config.hints && deck.size() < deckSize) {
            System.out.println();
            System.out.println("New Hints:");
            table.hints();
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        if (setQ.isEmpty()){
            try {
                long timeleft = reshuffleTime - System.currentTimeMillis();
                if (!firstSleep && timeleft > env.config.turnTimeoutWarningMillis)
                    Thread.sleep(1000);
                else
                    Thread.sleep(10); 
            } catch (InterruptedException e) {}
        }
        firstSleep = false;
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        if (reset){
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else{
            long delta = reshuffleTime - System.currentTimeMillis();
            if (delta > 0)
                env.ui.setCountdown(delta, delta <= env.config.turnTimeoutWarningMillis);
            else
                env.ui.setCountdown(0, true);
        }     
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {

        table.addToDeck(this);
        shuffleArray(slotsOrder);

        for (int slot = 0; slot < env.config.tableSize; slot++){
            table.removeCard(slotsOrder[slot]);

            // remove all the tokens from the removed cards
            table.rw.dealerLock();
            for (int i = 0; i < players.length; i++){
                players[i].removeToken(slotsOrder[slot]);
            }
            table.rw.dealerUnlock();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {

        //check max score and count winners
        int maxScore = 0;
        int counter = 0;
        for (int i = 0; i < players.length; i++){
            if (players[i].score() > maxScore){ 
                maxScore = players[i].score();
                counter = 1;
            }
            else if (players[i].score() == maxScore)
                counter++;
        }

        //add winner's id's to a new array
        int[] players_id = new int[counter];
        int j = 0;
        for (int i = 0; i < players.length; i++){
            if (players[i].score() == maxScore) {
                players_id[j] = i;
                j++;
            }
        }
        
        //announce winners
        env.ui.announceWinner(players_id);  
    }

    // Added
    public void pushToTestSet(Triple<Integer, int[], int[]> triple){
        setQ.put(triple);
        dealerThread.interrupt();
    }

    public void addCard(int card){
        deck.add(card);
    }
    
    // create random order to put cards on table
    private void shuffleArray(int[] arr){

        for (int i = slotsOrder.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);

            // Swap slotsOrder[i] and slotsOrder[j]
            int temp = slotsOrder[i];
            slotsOrder[i] = slotsOrder[j];
            slotsOrder[j] = temp;
        }
    }
}
