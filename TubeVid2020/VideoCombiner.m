function [ ] = VideoCombiner(Name1, Name2, OutputName, FrameRate)
% JLJ
% This function is meant to open two videos (before and after tube
% visualization) and combine the two videos into one so that it is easier
% to see a side by side comparison.
% Input are the names of the 2 videos to combine and then the output name

% WARNING - this function assumes that the two videos have the same number
%           of frames

Vid1 = VideoReader(Name1);
Vid2 = VideoReader(Name2);
fHeight = Vid1.Height;
fWidth  = Vid1.Width;
NewVid = VideoWriter(OutputName,'MPEG-4');
NewVid.FrameRate = FrameRate;
open(NewVid);
NewFrame(fHeight,fWidth*2,3)=uint8(0);
CombinedFrames = 1;
while hasFrame(Vid1)
    fLeft = readFrame(Vid1); % open frame from orginal video
    fRight = readFrame(Vid2); % open frame from newly made video
    NewFrame(:,1:fWidth,:) = fLeft;
    NewFrame(:,fWidth+1:2*fWidth,:) = fRight;
    writeVideo(NewVid,NewFrame);
    CombinedFrames = CombinedFrames +1
end
close(NewVid);
end