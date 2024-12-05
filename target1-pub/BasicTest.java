class BasicTest {
    static int fun2() {
        int x = 2;
        for (int i = 0; i < 5; i++) {
            x = x * 2;
        }
        return x;
    }

    static int fun1(int check) {
        int x = 10;
        if (check < 100) {
            x = x - 1;
        } else {
            x = x + 1;
        }

        return x + check;

    }

    static int fun3() {
        int x = 0;
        while (x >= 0) {
            x = x + 2;
        }
        return x;
    }

    static void foo(int n) {
        int i = n;
        while (i > 10) {
            i = i - 1;
        }
        int j = 9;
        int k = 1;
        while (j < 18) {
            j = j + k;
            k = k + 1;
        }
        int l = 0;
        if (i == j) {
            l = i;
        }
        k = l;
    }
}
