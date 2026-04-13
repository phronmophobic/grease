//
//  FromGluonAppDelegate.c
//  MobileTest
//
//  Created by Adrian Smith on 6/7/21.
//

#include <stdio.h>

//
//typedef struct {
//#ifdef GVM_IOS_SIM
//  char fCX8;
//  char fCMOV;
//  char fFXSR;
//  char fHT;
//  char fMMX;
//  char fAMD3DNOWPREFETCH;
//  char fSSE;
//  char fSSE2;
//  char fSSE3;
//  char fSSSE3;
//  char fSSE4A;
//  char fSSE41;
//  char fSSE42;
//  char fPOPCNT;
//  char fLZCNT;
//  char fTSC;
//  char fTSCINV;
//  char fAVX;
//  char fAVX2;
//  char fAES;
//  char fERMS;
//  char fCLMUL;
//  char fBMI1;
//  char fBMI2;
//  char fRTM;
//  char fADX;
//  char fAVX512F;
//  char fAVX512DQ;
//  char fAVX512PF;
//  char fAVX512ER;
//  char fAVX512CD;
//  char fAVX512BW;
//  char fAVX512VL;
//  char fSHA;
//  char fFMA;
//#else
//  char fFP;
//  char fASIMD;
//  char fEVTSTRM;
//  char fAES;
//  char fPMULL;
//  char fSHA1;
//  char fSHA2;
//  char fCRC32;
//  char fLSE;
//  char fSTXRPREFETCH;
//  char fA53MAC;
//  char fDMBATOMICS;
//#endif
//} CPUFeatures;
//
//void determineCPUFeatures(CPUFeatures* features)
//{
//    fprintf(stderr, "\n\n\ndetermineCpuFeaures\n");
//#ifdef GVM_IOS_SIM
//    features->fSSE = 1;
//    features->fSSE2 = 1;
//#else
//    features->fFP = 1;
//    features->fASIMD = 1;
//#endif
//}
//
//// dummy symbols only for JDK17
//void Java_java_net_AbstractPlainDatagramSocketImpl_isReusePortAvailable0() {}
//void Java_java_net_AbstractPlainSocketImpl_isReusePortAvailable0() {}
//void Java_java_net_DatagramPacket_init() {}
//
