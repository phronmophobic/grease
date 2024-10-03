(ns qrcode
  (:require [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]
            [babashka.fs :as fs]
            [membrane.ui :as ui]
            [clojure.java.io :as io]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [tech.v3.datatype.copy :as copy]
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.clj-libffi :as ffi]))

(defn bs->nsdata [bs]
  (let [len (alength bs)
        buf (native-buffer/malloc len)
        _ (copy/copy! bs buf)
        nsdata (objc
                [NSData :dataWithBytes:length
                 buf
                 len])]
    (objc/arc! nsdata)))

(defn bytes->qrcode [bs]
  (let [qrgen (objc [CIFilter :QRCodeGenerator])
        nsdata (bs->nsdata bs)
        _ (objc ^void [qrgen :setMessage nsdata])
        _ (objc ^void [qrgen :setCorrectionLevel @"H"])
        img (objc [qrgen :outputImage])
    
        context (objc [CIContext :contextWithOptions nil])
        extent (objc ^CGRect [img :extent])
        cgimg (objc [context :createCGImage:fromRect img extent])
    
        uiimg (objc [UIImage :imageWithCGImage cgimg])
        _ (ffi/call "CGImageRelease" :void :pointer cgimg)
        pngData (ffi/call "UIImagePNGRepresentation"
                          :pointer
                          :pointer
                          uiimg)

        len (objc ^long [pngData :length])
        buf (native-buffer/malloc len)
        _ (objc ^void [pngData :getBytes:length buf len])

        bs (byte-array len)]
    (copy/copy! buf bs)
    bs))

(defn -main []
  (let [bs (bytes->qrcode
            (.getBytes "https://github.com/phronmophobic/grease/blob/main/examples/app/qrcode.clj"
                       "ascii"))
        w (ui/width (ui/image bs))
        size (* 3 (quot
                   (min (:width app/screen-size)
                        (:height app/screen-size))
                   4))

        view (ui/center
              (ui/image bs [size size])
              [(:width app/screen-size)
               (:height app/screen-size)])]
    
    (app/show! {:view-fn (constantly view)})))
