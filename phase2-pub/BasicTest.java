class BasicTest{
  public static void foo() {
        int[] a;
        int[] b = new int[3];
        int[] c = new int[10];
        if (b[0] == 1) {
            a = b;
        } else {
            a = c;
        }

    	int i = 0;
        while (i < 2) {
            a[i] = a[i+1];  // Both indexes safe
            i++;
        }
    }

    public static void bar() {
        int[] a;
        int[] b = new int[3];
        int[] c = new int[10];
        if (b[0] == 1) {
            a = b;
        } else {
            a = c;
        }

	    int i = 0;
        while (i < 9) {
            a[i] = a[i+1];  // Both indexes potentially unsafe
            i++;
        }
    }

    public static void foo2() {
        int[] a = new int[10];
        int[] b;
        a[1] = 10;
        if (a[1] == 9) {
            b = a;
        }
        b[0] = 1; // b may point to a, Null; access unsafe
    }
}