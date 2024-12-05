class BasicTest {
    static void foo(int n) {
        int i = n;
        while (i > 10) {
            i--;
        }
        int j = 9;
        int k = 1;
        while (j < 18) { 
            j = j + k; 
            k = k + 1; 
        }
        if (i == j) {
            int l = i;
            k = l;
        }
    }
}