class BasicTest{
    static int fun2(){
        int x = 2;
        for(int i = 0; i < 5; i++){
            x = x*2;
        }
        return x;
    }
    static int fun1(int check){
        int x = 10;
        if(check < 100){
            x = x - 1;
        }
        else{
            x = x + 1;
        }

        return x+check;

    }

    static int fun3(){
        int x = 0;
        while(x >=0){
            x = x + 2;
        }
        return x;
    }
}




