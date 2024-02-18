package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Collections;

import java.util.List;
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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        //added
        terminate = false;
        playersThreads = new Thread[players.length];
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
        for (int i = 0; i < players.length; i++){
            playersThreads[i] = new Thread(players[i]);
        }
        for (int i = 0; i < playersThreads.length; i++){
            playersThreads[i].start();
        }
        
        while (!shouldFinish()) {

            // Added
            Collections.shuffle(deck);

            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {

        // Added
        reshuffleTime = System.currentTimeMillis() + 60000;

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
        // TODO implement

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
                    for (int slot = 0; slot < slots.length; slot++){
                        table.removeCard(slot);

                        // remove all the tokens from the removed cards
                        for (int i = 0; i < players.length; i++){
                            players[i].removeToken(slot);
                        }
                    }
                }

                //this is not a legal set, so remove tokens (and update tokens counter)
                else{ 
                    for(int i = 0 ; i < slots.length ; i++){
                        players[playerId].removeToken(slots[i]);
                    }
                }
            }

            // wake player and pop from queue
            playersThreads[playerId].interrupt();
            if (toUpdateTimer) {
                updateTimerDisplay(true);
                reshuffleTime = System.currentTimeMillis() + 60000;
            } 
            toCheck = setQ.take();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {

        // For each slot that equals null, remove the first card in the deck and place it on the table
        for (int i = 0; i < env.config.tableSize; i++){
            if (table.slotToCard[i] == null){
                int card = deck.remove(0);
                table.placeCard(card, i); // NEEDED TO BE SYNCHRONIZED
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        try {
            Thread.sleep(10); // CHANGE TIME???
        } catch (InterruptedException e) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        if (reset){
            env.ui.setCountdown(60000, false);
        }
        else{
            long now = System.currentTimeMillis();
            env.ui.setCountdown(reshuffleTime - now, reshuffleTime - now < env.config.turnTimeoutWarningMillis);
        }
            
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {

        table.addToDeck(this);

        for (int slot = 0; slot < env.config.tableSize; slot++){
            table.removeCard(slot);

            // remove all the tokens from the removed cards
            for (int i = 0; i < players.length; i++){
                players[i].removeToken(slot);
            }
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
        int j=0;
        for (int i = 0; i < players.length; i++){
            if (players[i].score() == maxScore) 
            players_id[j] = i;
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
}
