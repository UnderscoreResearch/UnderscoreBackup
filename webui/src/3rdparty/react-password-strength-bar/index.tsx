import React, {CSSProperties, Fragment, ReactNode} from 'react';

// components
import Item from './Item';

export interface PasswordFeedback {
    warning?: string;
    suggestions?: string[];
}

interface PasswordStrengthBarState {
    score: number;
}

export interface PasswordStrengthBarProps {
    className?: string;
    style?: CSSProperties;
    scoreWordClassName?: string;
    scoreWordStyle?: CSSProperties;
    password: string;
    userInputs?: string[];
    barColors?: string[];
    scoreWords?: ReactNode[];
    minLength?: number;
    shortScoreWord?: ReactNode;
    onChangeScore?: (
        score: PasswordStrengthBarState['score'],
        feedback: PasswordFeedback,
    ) => void;
}

const rootStyle: CSSProperties = {
    position: 'relative',
};

const wrapStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    margin: '5px 0 0',
};

const spaceStyle: CSSProperties = {
    width: 4,
};

const descStyle: CSSProperties = {
    margin: '5px 0 0',
    color: '#898792',
    fontSize: 14,
    textAlign: 'right',
};

class PasswordStrengthBar extends React.Component<
    PasswordStrengthBarProps,
    PasswordStrengthBarState
> {
    public static defaultProps: PasswordStrengthBarProps = {
        className: undefined,
        style: undefined,
        scoreWordClassName: undefined,
        scoreWordStyle: undefined,
        password: '',
        userInputs: [],
        barColors: ['#ddd', '#ef4836', '#f6b44d', '#2b90ef', '#25c281'],
        scoreWords: ['too weak', 'too weak', 'okay', 'good', 'strong'],
        minLength: 8,
        shortScoreWord: 'too short',
        onChangeScore: undefined,
    };

    public state = {
        score: 0,
    };

    public componentDidMount(): void {
        this.setScore();
    }

    public componentDidUpdate(prevProps: PasswordStrengthBarProps): void {
        const {password} = this.props;
        if (prevProps.password !== password) {
            this.setScore();
        }
    }

    public render(): ReactNode {
        const {
            className,
            style,
            scoreWordClassName,
            scoreWordStyle,
            password,
            barColors,
            scoreWords,
            minLength,
            shortScoreWord,
        } = this.props;
        const {score} = this.state;
        const newShortScoreWord =
            password.length >= (minLength as number) ? (scoreWords as string[])[score] : shortScoreWord;

        return (
            <div className={className} style={{...rootStyle, ...style}}>
                <p
                    className={scoreWordClassName}
                    style={{...descStyle, ...scoreWordStyle}}
                >
                    {newShortScoreWord}
                </p>
                <div style={wrapStyle}>
                    {[1, 2, 3, 4].map((el: number) => (
                        <Fragment key={`password-strength-bar-item-${el}`}>
                            {el > 1 && <div style={spaceStyle}/>}
                            <Item score={score} itemNum={el} barColors={barColors as string[]}/>
                        </Fragment>
                    ))}
                </div>
            </div>
        );
    }

    private setScore = (): void => {
        const {password, minLength, userInputs, onChangeScore} = this.props;

        async function updateScore(parent: PasswordStrengthBar): Promise<void> {
            const {score, feedback} = await import('./passwordResolve').then((module) =>
                module.default(password, minLength, userInputs)
            );
            parent.setState(
                {
                    score,
                },
                () => {
                    if (onChangeScore) {
                        onChangeScore(score, feedback);
                    }
                }
            );
        }

        updateScore(this);
    };
}

export default PasswordStrengthBar;
