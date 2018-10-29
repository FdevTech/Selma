# Selma
---
selma is virtual assistant built by Algerian engineers in **2018** to  control different appliances in your home over bluetooth low energy this project touches used diffrent technologies such as **dialogflow TensorFlow RxJava bluetooth low energy** so to get started let's have look on the project architecture 
![architecture](https://user-images.githubusercontent.com/38364385/47667444-472d3800-dba6-11e8-882f-ee77cc7a142b.jpg)
## dimmer:
the dimmer is built around **nrf51822 beacon** which can be controled by using **bluetooth low energy** the electrical scheme of the dimmer is illustrated if the figure bellow 
![the electrical scheme](https://user-images.githubusercontent.com/38364385/47671565-1b16b480-dbb0-11e8-86c4-552f657d7be5.png)
the code source of the dimmer you can find it [here](https://github.com/ceristTeam/Dimmer dimmer), to get started with bluetooth low energy you can check these  posts on medium [first part](https://medium.com/mindorks/bluetooth-low-energy-3656ac323c4e) and the [seconde part](https://medium.com/mindorks/bluetooth-low-energy-on-raspberry-second-part-516b5e8ad7c2)

## Augmented reality
to build augmented reality features we have used **SDD (single shot multibox detector)** our model can recognize three objects **air conditioner TV lamp**, we have created our model by using **TensorFlow** bellow some images of our detections 

 ![detection of the lamp ](https://user-images.githubusercontent.com/38364385/47671463-d3902880-dbaf-11e8-9ab8-576ecbd5ceec.png)
