package uk.co.httpsmmuminecraftsociety.mainmod.utils;

public record Tuple<A, B>(A a, B b)
{
    public A getA()
    {
        return a;
    }

    public B getB()
    {
        return b;
    }
}
