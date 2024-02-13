CLASS_PATH = .

JCC = javac

JVM= java

all: MyDedup.class

MyDedup.class: MyDedup.java
	$(JCC) -cp $(CLASS_PATH) MyDedup.java

clean:
	$(RM) *.class
	$(RM) *.txt
	$(RM) *.index
	$(RM) -r fileRecipes

run: MyDedup.class
	$(JVM) -cp $(CLASS_PATH) MyDedup $(ARGS)
