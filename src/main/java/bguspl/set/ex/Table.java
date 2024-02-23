package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    // Added
    private final boolean[][] tokens;
    protected ReaderWriter rw;
    private boolean canPlaceTokens;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;

        // Added
        tokens = new boolean[env.config.players][env.config.tableSize];
        for (int i = 0; i < tokens.length; i++)
            for (int j = 0; j < tokens[i].length; j++)
                tokens[i][j] = false;
        
        rw = new ReaderWriter();
        canPlaceTokens = false;
    }
    

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        rw.dealerLock();
        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        rw.dealerUnlock();

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        rw.dealerLock();
        Integer card = slotToCard[slot];
        if(card != null){
            cardToSlot[card] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
        }
        rw.dealerUnlock();

    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        
        tokens[player][slot] = true;
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) { // Needs to be synched from the outside
        // TODO implement
        boolean output = tokens[player][slot];
        if (output){
            tokens[player][slot] = false;
            env.ui.removeToken(player, slot);
        }
        return output;
    }

    //this method returns a 2-D array which the first array is the cards, and the second one is the slots each card
    //is the corresponding slot, for a specific player.
    public int[][] returnSet(int player){
        int j = 0;
        int[][] output = new int[2][3];

        rw.playerLock();
        for (int i = 0; i < tokens[player].length && output != null; i++){
            if (tokens[player][i]){
                if (slotToCard[i] == null)
                    output = null;
                else{
                    output[0][j] = slotToCard[i];
                    output[1][j] = i;
                    j++;
                }
            }
        }
        rw.playerUnlock();

        return output;
    }

    public boolean isSetRelevant(int[] cards, int[] slots){
        boolean output = false;
        rw.dealerLock();
        if (slotToCard[slots[0]] != null && slotToCard[slots[1]] != null && slotToCard[slots[2]] != null)
            output = (cards[0] == slotToCard[slots[0]] && cards[1] == slotToCard[slots[1]] && cards[2] == slotToCard[slots[2]]);
        rw.dealerUnlock();
        return output;
    }

    public void addToDeck(Dealer dealer){
        rw.dealerLock();
        for (int i = 0; i < slotToCard.length; i++){
            if (slotToCard[i] != null)
                dealer.addCard(slotToCard[i]);
        }
        rw.dealerUnlock();
    }

    public Integer getCard(int slot){
        rw.playerLock();
        Integer output = slotToCard[slot];
        rw.playerUnlock();
        return output;
    }

    public boolean getToken(int id, int slot){
        rw.playerLock();
        boolean output =  tokens[id][slot];
        rw.playerUnlock();
        return output;
    }

    public boolean ourPlaceToken(int player, int slot){
        rw.playerLock();
        boolean output = slotToCard[slot] != null;
        if (output)
            placeToken(player, slot);
        rw.playerUnlock();
        return output;
    }

    public boolean getCanPlaceToken(){
        boolean output;
        rw.playerLock();
        output = canPlaceTokens;
        rw.playerUnlock();
        return output;
    }

    public void setCanPlaceToken(boolean newVal){
        rw.dealerLock();
        canPlaceTokens = newVal;
        rw.dealerUnlock();
    }
}
