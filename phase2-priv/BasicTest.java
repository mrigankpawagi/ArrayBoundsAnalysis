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
}