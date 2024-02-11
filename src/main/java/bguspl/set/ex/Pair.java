package bguspl.set.ex;

class Pair<E, T>{
    
    private E first;
    private T second;

    public Pair(E first, T second){
        this.first = first;
        this.second = second;
    }

    public E getFirst(){
        return first;
    }

    public T getSecond(){
        return second;
    }
}