@ECHO OFF
java -cp . gcl.GCLCompiler ..\tests\test0.fix ..\tests\results\macc\test0fixlist.txt
java -cp . macc.Macc3 <..\tests\test0.dat >..\tests\results\macc\test0fix.result

java -cp . gcl.GCLCompiler ..\tests\test1_1 ..\tests\results\macc\test1_1list.txt
..\macc3-pc.exe <..\tests\test1_1.dat >..\tests\results\macc\test1_1.result

java -cp . gcl.GCLCompiler ..\tests\test1_2 ..\tests\results\macc\test1_2list.txt
..\macc3-pc.exe <..\tests\test1_1.dat >..\tests\results\macc\test1_2.result

java -cp . gcl.GCLCompiler ..\tests\test2 ..\tests\results\macc\test2list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test2.result

java -cp . gcl.GCLCompiler ..\tests\test3 ..\tests\results\macc\test3list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test3.result

java -cp . gcl.GCLCompiler ..\tests\test4 ..\tests\results\macc\test4list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test4.result

java -cp . gcl.GCLCompiler ..\tests\test4_1 ..\tests\results\macc\test4_1list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test4_1.result

java -cp . gcl.GCLCompiler ..\tests\test5 ..\tests\results\macc\test5list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test5.result

java -cp . gcl.GCLCompiler ..\tests\test5_1 ..\tests\results\macc\test5_1list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test5_1.result

java -cp . gcl.GCLCompiler ..\tests\test6 ..\tests\results\macc\test6list.txt

java -cp . gcl.GCLCompiler ..\tests\test7 ..\tests\results\macc\test7list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test7.result

java -cp . gcl.GCLCompiler ..\tests\test8 ..\tests\results\macc\test8list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test8.result

java -cp . gcl.GCLCompiler ..\tests\test9 ..\tests\results\macc\test9list.txt
java -cp . macc.Macc3 <..\tests\test9.dat >..\tests\results\macc\test9.result

java -cp . gcl.GCLCompiler ..\tests\test9_1 ..\tests\results\macc\test9_1list.txt
java -cp . macc.Macc3 <..\tests\test9_1.dat >..\tests\results\macc\test9_1.result

java -cp . gcl.GCLCompiler ..\tests\test9_2 ..\tests\results\macc\test9_2list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test9_2.result

java -cp . gcl.GCLCompiler ..\tests\test9_3 ..\tests\results\macc\test9_3list.txt

java -cp . gcl.GCLCompiler ..\tests\test10 ..\tests\results\macc\test10list.txt

java -cp . gcl.GCLCompiler ..\tests\test10_1 ..\tests\results\macc\test10_1list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test10_1.result

java -cp . gcl.GCLCompiler ..\tests\test10_2 ..\tests\results\macc\test10_2list.txt

java -cp . gcl.GCLCompiler ..\tests\test11 ..\tests\results\macc\test11list.txt
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11.result

java -cp . gcl.GCLCompiler ..\tests\test11_1 ..\tests\results\macc\test11_1list.txt
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_1.result

java -cp . gcl.GCLCompiler ..\tests\test11_2 ..\tests\results\macc\test11_2list.txt
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_2.result

::java -cp . gcl.GCLCompiler ..\tests\test11_3 ..\tests\results\macc\test11_3list.txt
::java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_3.result

java -cp . gcl.GCLCompiler ..\tests\test11_3.fix ..\tests\results\macc\test11_3fixlist.txt
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_3fix.result

java -cp . gcl.GCLCompiler ..\tests\test11_4 ..\tests\results\macc\test11_4list.txt 
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_4.result

java -cp . gcl.GCLCompiler ..\tests\test11_5 ..\tests\results\macc\test11_5list.txt
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_5.result

java -cp . gcl.GCLCompiler ..\tests\test11_6 ..\tests\results\macc\test11_6list.txt
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_6.result

java -cp . gcl.GCLCompiler ..\tests\Test11_7 ..\tests\results\macc\Test11_7list.txt

java -cp . gcl.GCLCompiler ..\tests\test11_8 ..\tests\results\macc\test11_8list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test11_8.result

java -cp . gcl.GCLCompiler ..\tests\test11_9 ..\tests\results\macc\test11_9list.txt
java -cp . macc.Macc3 <..\tests\test11.dat >..\tests\results\macc\test11_9.result

java -cp . gcl.GCLCompiler ..\tests\test12 ..\tests\results\macc\test12list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test12.result

java -cp . gcl.GCLCompiler ..\tests\test12_1 ..\tests\results\macc\test12_1list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test12_1.result

java -cp . gcl.GCLCompiler ..\tests\test12_2 ..\tests\results\macc\test12_2list.txt

java -cp . gcl.GCLCompiler ..\tests\test13 ..\tests\results\macc\test13list.txt
java -cp . macc.Macc3 <..\tests\test13.dat >..\tests\results\macc\test13.result

java -cp . gcl.GCLCompiler ..\tests\test13_1 ..\tests\results\macc\test13_1list.txt
java -cp . macc.Macc3 <..\tests\test13.dat >..\tests\results\macc\test13_1.result

java -cp . gcl.GCLCompiler ..\tests\test14 ..\tests\results\macc\test14list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test14.result

java -cp . gcl.GCLCompiler ..\tests\test14_1 ..\tests\results\macc\test14_1list.txt
java -cp . macc.Macc3 <..\tests\test14_1.dat >..\tests\results\macc\test14_1.result

java -cp . gcl.GCLCompiler ..\tests\test15 ..\tests\results\macc\test15list.txt
java -cp . macc.Macc3 <..\tests\test15.dat >..\tests\results\macc\test15.result

java -cp . gcl.GCLCompiler ..\tests\test16 ..\tests\results\macc\test16list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test16.result

java -cp . gcl.GCLCompiler ..\tests\test16_1 ..\tests\results\macc\test16_1list.txt

java -cp . gcl.GCLCompiler ..\tests\test16_2 ..\tests\results\macc\test16_2list.txt
java -cp . macc.Macc3 <..\tests\test16_2.dat >..\tests\results\macc\test16_2.result

java -cp . gcl.GCLCompiler ..\tests\test16_3 ..\tests\results\macc\test16_3list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test16_3.result

java -cp . gcl.GCLCompiler ..\tests\test17 ..\tests\results\macc\test17list.txt
java -cp . macc.Macc3 <..\tests\test17.dat >..\tests\results\macc\test17.result

java -cp . gcl.GCLCompiler ..\tests\test17_1 ..\tests\results\macc\test17_1list.txt
java -cp . macc.Macc3 <..\tests\test17_1.dat >..\tests\results\macc\test17_1.result

java -cp . gcl.GCLCompiler ..\tests\test17_2 ..\tests\results\macc\test17_2list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test17_2.result

java -cp . gcl.GCLCompiler ..\tests\test17_3 ..\tests\results\macc\test17_3list.txt
java -cp . macc.Macc3 <..\tests\test17_3.dat >..\tests\results\macc\test17_3.result

java -cp . gcl.GCLCompiler ..\tests\test17_4 ..\tests\results\macc\test17_4list.txt

java -cp . gcl.GCLCompiler ..\tests\test18 ..\tests\results\macc\test18list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test18.result

java -cp . gcl.GCLCompiler ..\tests\test18_1fix ..\tests\results\macc\test18_1fixlist.txt
java -cp . macc.Macc3 >..\tests\results\macc\test18_1fix.result

java -cp . gcl.GCLCompiler ..\tests\test18_2 ..\tests\results\macc\test18_2list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test18_2.result

java -cp . gcl.GCLCompiler ..\tests\test18_3 ..\tests\results\macc\test18_3list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test18_3.result

java -cp . gcl.GCLCompiler ..\tests\test19 ..\tests\results\macc\test19list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test19.result

::java -cp . gcl.GCLCompiler ..\tests\test19x ..\tests\results\macc\test19xlist.txt
::::..\sam3-pc.exe
::java -cp . macc.Macc3 >..\tests\results\macc\test19x.result

java -cp . gcl.GCLCompiler ..\tests\test19_1 ..\tests\results\macc\test19_1list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test19_1.result

java -cp . gcl.GCLCompiler ..\tests\test19_2 ..\tests\results\macc\test19_2list.txt

java -cp . gcl.GCLCompiler ..\tests\test20 ..\tests\results\macc\test20list.txt

java -cp . gcl.GCLCompiler ..\tests\test20_1 ..\tests\results\macc\test20_1list.txt
java -cp . macc.Macc3 >..\tests\results\macc\test20_1.result

java -cp . gcl.GCLCompiler ..\tests\test20_2 ..\tests\results\macc\test20_2list.txt