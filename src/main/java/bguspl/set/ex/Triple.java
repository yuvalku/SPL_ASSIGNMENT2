package bguspl.set.ex;

class Triple<E, T, V>{
    
    private E first;
    private T second;
    private V third;

    public Triple(E first, T second, V third){
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public E getFirst(){
        return first;
    }

    public T getSecond(){
        return second;
    }

    public V getThird(){
        return third;
    }
}