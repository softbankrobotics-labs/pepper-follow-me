# Pepper Follow Me library

This Android Library allows you to make Pepper follow a human.

This library gets a [`Human` object](https://developer.softbankrobotics.com/pepper-qisdk/api/perceptions/reference/human#human) representing the person to follow as parameter. You can then choose when to start or to stop following the person.
Callbacks are available to know if the robot has started following the person, if the robot is stuck or if the distance between the robot and the person has changed.

## Getting Started


### Prerequisites

A robotified project for Pepper with QiSDK. Read the [documentation](https://developer.softbankrobotics.com/pepper-qisdk) if needed.

### Running the Sample Application

The project comes complete with a sample project. You can clone the repository, open it in Android Studio, and run this directly onto a robot.

The sample demonstrate how to get a `Human` object and how use the library in a project.

Full implementation details are available to see in the project.

### Installing

[**Follow these instructions**](https://jitpack.io/#softbankrobotics-labs/pepper-follow-me)

Make sure to replace 'Tag' by the number of the version of the library you want to use.


## Usage

*This README assumes some standard setup can be done by the user, such as initialising variables or implementing code in the correct functions. Refer to the Sample Project for full usage code.*

Initialise the QiSDK in the onCreate. If you are unsure how to do this, refer to the [QiSDK tutorials](https://developer.softbankrobotics.com/pepper-qisdk/getting-started/creating-robot-application)
```
QiSDK.register(this, this)
```
Once you get the robot focus in the `onRobotFocusGained` method,  you need to find the person you want to follow. See [this tutorial](https://developer.softbankrobotics.com/pepper-qisdk/api/perceptions/tutorials/humanawareness-human) to learn how to get the `Human`objects representing people around the robot. You can also use [HumanAwareness.OnHumansAroundChangedListener](https://qisdk.softbankrobotics.com/sdk/doc/qisdk/com/aldebaran/qi/sdk/object/humanawareness/HumanAwareness.OnHumansAroundChangedListener.html) to monitor the people coming in and out of Pepper's sight.
When you have defined the `Human` object corresponding to the person you want the robot to follow, create a `FollowHuman` object from the library:
```
followHuman = FollowHuman(qiContext, humanToFollow)
```
Start following the person whenever you want (for instance after a Listen action where the person asks the robot to follow them):
```
followHuman.start()
```
Start the solitaries loop whenever you want (for instance after a Listen action where the person asks the robot to stop following them):
```
followHuman.stop()
```
You can also use the three callbacks from the library:
- onFollowingHuman: this callback is trigerred when the robot starts following the person. It can be use to update the UI for instance.
- onCantReachHuman: this callback is triggered when the robot is stuck, ie. no moving action succeded during a 5 seconds period. When this callback is triggered, the robot is always trying to follow the person. You can use this callback to make to robot say that it's stuck or to stop following the person.
- onDistanceToHumanChanged: this callback gives the distance between the robot and the person to follow. It is called every second. This can be used to make the robot stop following the person if it's too close to them (meaning the person has been reached and there's no need to follow them anymore) or to make the robot say to wait for it.
To use this callbacks, you have to implement the `FollowHumanListener` interface. For instance, if you make you Activity implement it, you can then create the `FollowHuman` object like this:
```
followHuman = FollowHuman(qiContext, humanToFollow, this)
```


## Known limitations

Pepper tracks "3D blobs", and decides based on face detection which of those blobs are humans", that way Pepper can keep following you even if you turn away from her, but since your face is not visible you will be lost more easily.
For more information on how this works in the QiSDK, please see the [Human API](https://developer.softbankrobotics.com/pepper-qisdk/api/perceptions/reference/human#human).


## License

This project is licensed under the BSD 3-Clause "New" or "Revised" License. See the [LICENSE](LICENSE.md) file for details.
